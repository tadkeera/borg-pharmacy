package com.borgpharmacy.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.borgpharmacy.backup.BackupService
import com.borgpharmacy.data.local.AppSettingEntity
import com.borgpharmacy.data.local.BorgDatabase
import com.borgpharmacy.data.local.CompanyEntity
import com.borgpharmacy.data.local.PrintLogEntity
import com.borgpharmacy.data.local.RepresentativeEntity
import com.borgpharmacy.data.local.TierCountTuple
import com.borgpharmacy.data.local.UserEntity
import com.borgpharmacy.data.local.VisitEntity
import com.borgpharmacy.data.local.toDomain
import com.borgpharmacy.data.local.toEntity
import com.borgpharmacy.data.remote.SupabaseSyncService
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.CompanyReportScore
import com.borgpharmacy.domain.CycleCalculator
import com.borgpharmacy.domain.PrintCount
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.ScheduleGenerator
import com.borgpharmacy.domain.Tier
import com.borgpharmacy.domain.UserAccount
import com.borgpharmacy.domain.UserRole
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.VisitStatus
import com.borgpharmacy.security.SecurityHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

interface BorgRepository {
    fun observeCompanies(): Flow<List<Company>>
    fun observeRepresentatives(): Flow<List<Representative>>
    fun observeVisits(): Flow<List<Visit>>
    fun observePrintCounts(): Flow<List<PrintCount>>
    fun observeUsers(): Flow<List<UserAccount>>
    fun observeTierCounts(): Flow<List<TierCountTuple>>

    suspend fun initialize(): LocalDate
    suspend fun cycleStart(): LocalDate
    suspend fun login(username: String, passcode: String): UserAccount?
    suspend fun changePasscode(userId: String, newPasscode: String)
    suspend fun createUser(username: String, displayName: String, role: UserRole, passcode: String)

    suspend fun addCompany(name: String): Company
    suspend fun importCompaniesCsv(csv: String): Int
    suspend fun updateCompanyTier(companyId: String, tier: Tier)
    suspend fun deleteCompany(companyId: String)
    suspend fun addRepresentative(companyId: String, name: String, phone: String): Representative
    suspend fun deleteRepresentative(repId: String)
    suspend fun setVisitStatus(visitId: String, status: VisitStatus)
    suspend fun recordPrint(repId: String, visitId: String)
    suspend fun rescheduleCurrentCycle()
    suspend fun syncNow()
    suspend fun backupNow(reason: String = "manual")
    suspend fun dashboardScores(): List<CompanyReportScore>
}

class OfflineFirstBorgRepository(
    private val db: BorgDatabase,
    private val backupService: BackupService,
    private val syncService: SupabaseSyncService,
    private val scheduleGenerator: ScheduleGenerator = ScheduleGenerator(),
    private val cycleCalculator: CycleCalculator = CycleCalculator(),
) : BorgRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeCompanies(): Flow<List<Company>> = db.companyDao().observeActive().map { list -> list.map { it.toDomain() } }
    override fun observeRepresentatives(): Flow<List<Representative>> = db.representativeDao().observeActive().map { list -> list.map { it.toDomain() } }
    override fun observeVisits(): Flow<List<Visit>> = db.visitDao().observeActive().map { list -> list.map { it.toDomain() } }
    override fun observePrintCounts(): Flow<List<PrintCount>> = db.printLogDao().observeCounts().map { rows -> rows.map { PrintCount(it.repId, it.visitId, it.count) } }
    override fun observeUsers(): Flow<List<UserAccount>> = db.userDao().observeActive().map { list -> list.map { it.toDomain() } }
    override fun observeTierCounts(): Flow<List<TierCountTuple>> = db.companyDao().observeTierCounts()

    override suspend fun initialize(): LocalDate {
        backupService.ensureDirectories()
        seedDefaultAdmin()
        val start = cycleStart()
        backupService.dumpDatabase("launch")
        return start
    }

    override suspend fun cycleStart(): LocalDate {
        val key = "cycle_start_epoch_day"
        val current = db.appSettingsDao().getValue(key)?.toLongOrNull()
        if (current != null) return LocalDate.ofEpochDay(current)
        val today = LocalDate.now()
        db.appSettingsDao().set(AppSettingEntity(key, today.toEpochDay().toString()))
        return today
    }

    private suspend fun seedDefaultAdmin() {
        if (db.userDao().count() == 0) {
            db.userDao().upsert(
                UserEntity(
                    username = "admin",
                    displayName = "Master Admin",
                    role = UserRole.ADMIN.name,
                    passcodeHash = SecurityHasher.hashPasscode("admin2026"),
                    mustChangePasscode = true,
                )
            )
        }
    }

    override suspend fun login(username: String, passcode: String): UserAccount? {
        val user = db.userDao().findByUsername(username.trim()) ?: return null
        return if (SecurityHasher.verify(passcode, user.passcodeHash)) user.toDomain() else null
    }

    override suspend fun changePasscode(userId: String, newPasscode: String) {
        db.userDao().changePasscode(userId, SecurityHasher.hashPasscode(newPasscode))
        afterMutation("passcode")
    }

    override suspend fun createUser(username: String, displayName: String, role: UserRole, passcode: String) {
        db.userDao().upsert(
            UserEntity(
                username = username.trim(),
                displayName = displayName.trim().ifBlank { username.trim() },
                role = role.name,
                passcodeHash = SecurityHasher.hashPasscode(passcode),
                mustChangePasscode = false,
            )
        )
        afterMutation("user")
    }

    override suspend fun addCompany(name: String): Company {
        val company = Company(name = name.trim().ifBlank { "Unnamed Company" })
        db.companyDao().upsert(company.toEntity())
        afterMutation("company")
        return company
    }

    override suspend fun importCompaniesCsv(csv: String): Int {
        val rows = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .dropWhile { it.lowercase().contains("company") || it.lowercase().contains("name") }
            .map { line -> line.split(',', ';', '\t').firstOrNull()?.trim().orEmpty() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .map { CompanyEntity(name = it) }
            .toList()
        if (rows.isNotEmpty()) {
            db.companyDao().upsertAll(rows)
            afterMutation("csv_import")
        }
        return rows.size
    }

    override suspend fun updateCompanyTier(companyId: String, tier: Tier) {
        db.companyDao().updateTier(companyId, tier.name)
        afterMutation("tier")
    }

    override suspend fun deleteCompany(companyId: String) {
        db.withTransaction {
            db.companyDao().softDelete(companyId)
            db.visitDao().softDeleteForCompany(companyId)
        }
        afterMutation("company_delete")
    }

    override suspend fun addRepresentative(companyId: String, name: String, phone: String): Representative {
        val rep = Representative(companyId = companyId, name = name.trim(), phone = normalizePhone(phone))
        db.representativeDao().upsert(rep.toEntity())
        afterMutation("representative")
        return rep
    }

    override suspend fun deleteRepresentative(repId: String) {
        db.representativeDao().softDelete(repId)
        afterMutation("representative_delete")
    }

    override suspend fun setVisitStatus(visitId: String, status: VisitStatus) {
        db.visitDao().updateStatus(visitId, status.name)
        afterMutation("visit_status")
    }

    override suspend fun recordPrint(repId: String, visitId: String) {
        db.printLogDao().insert(PrintLogEntity(repId = repId, visitId = visitId))
        afterMutation("print")
    }

    override suspend fun rescheduleCurrentCycle() {
        val start = cycleStart()
        val companies = db.companyDao().search("").map { it.toDomain() }
        val visits = db.visitDao().listCycle(start.toEpochDay()).map { it.toDomain() }
        val plan = scheduleGenerator.reconcile(start, companies, visits)
        db.withTransaction {
            val deletes = plan.visitsToSoftDelete.map { it.id }
            if (deletes.isNotEmpty()) db.visitDao().softDeleteByIds(deletes)
            if (plan.visitsToUpsert.isNotEmpty()) db.visitDao().upsertAll(plan.visitsToUpsert.map { it.toEntity() })
        }
        afterMutation("schedule")
    }

    override suspend fun syncNow() {
        try {
            val companies = db.companyDao().dirty()
            val reps = db.representativeDao().dirty()
            val visits = db.visitDao().dirty()
            syncService.pushCompanies(companies)
            syncService.pushRepresentatives(reps)
            syncService.pushVisits(visits)
            if (companies.isNotEmpty()) db.companyDao().markClean(companies.map { it.id })
            if (reps.isNotEmpty()) db.representativeDao().markClean(reps.map { it.id })
            if (visits.isNotEmpty()) db.visitDao().markClean(visits.map { it.id })

            val remote = syncService.pullAll()
            db.withTransaction {
                if (remote.companies.isNotEmpty()) db.companyDao().upsertAll(remote.companies)
                if (remote.representatives.isNotEmpty()) db.representativeDao().upsertAll(remote.representatives)
                if (remote.visits.isNotEmpty()) db.visitDao().upsertAll(remote.visits)
            }
        } catch (throwable: Throwable) {
            Log.w("BorgSync", "Cloud sync skipped/failed; local cache remains authoritative", throwable)
        }
    }

    override suspend fun backupNow(reason: String) {
        backupService.dumpDatabase(reason)
    }

    override suspend fun dashboardScores(): List<CompanyReportScore> {
        val companies = db.companyDao().search("").map { it.toDomain() }.filter { it.tier.visitsPerCycle > 0 }
        val visits = db.visitDao().listCycle(cycleStart().toEpochDay()).map { it.toDomain() }
        return companies.map { company ->
            val expected = company.tier.visitsPerCycle
            val completed = visits.count { it.companyId == company.id && it.status == VisitStatus.COMPLETED }
            val score = if (expected == 0) 0.0 else (completed.toDouble() / expected.toDouble()) * 10.0
            CompanyReportScore(company, expected, completed, score.coerceAtMost(10.0))
        }
    }

    private fun normalizePhone(input: String): String {
        val trimmed = input.trim().ifBlank { "+967" }
        return if (trimmed.startsWith("+")) trimmed else "+967$trimmed"
    }

    private fun afterMutation(reason: String) {
        scope.launch { backupService.dumpDatabase(reason) }
        scope.launch { syncNow() }
    }
}
