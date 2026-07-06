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
import com.borgpharmacy.domain.Shift
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
    suspend fun restoreSavedSession(): UserAccount?
    suspend fun clearSavedSession()
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

private const val SESSION_USER_ID_KEY = "session_user_id"

private data class StableSlot(
    val date: LocalDate,
    val shift: Shift,
    val slotIndex: Int,
    val score: Int,
)

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
        scope.launch { syncNow() }
        return start
    }

    override suspend fun cycleStart(): LocalDate {
        // Borg Pharmacy fixed 28-day cycle anchor:
        // Saturday 04 July 2026 is treated as Day 1 / Week 1.
        // Every 28 days the same four-week table rotates, regardless of calendar month.
        val fixedBaseline = LocalDate.of(2026, 7, 4)
        val currentCycleStart = cycleCalculator.currentCycle(fixedBaseline, LocalDate.now()).currentCycleStart
        db.appSettingsDao().set(AppSettingEntity("fixed_cycle_baseline_epoch_day", fixedBaseline.toEpochDay().toString()))
        db.appSettingsDao().set(AppSettingEntity("current_cycle_start_epoch_day", currentCycleStart.toEpochDay().toString()))
        return currentCycleStart
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
        if (!SecurityHasher.verify(passcode, user.passcodeHash)) return null
        if (!user.mustChangePasscode) saveSession(user.id)
        return user.toDomain()
    }

    override suspend fun restoreSavedSession(): UserAccount? {
        val userId = db.appSettingsDao().getValue(SESSION_USER_ID_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val user = db.userDao().getById(userId) ?: return null
        return if (user.mustChangePasscode) null else user.toDomain()
    }

    override suspend fun clearSavedSession() {
        db.appSettingsDao().set(AppSettingEntity(SESSION_USER_ID_KEY, ""))
    }

    private suspend fun saveSession(userId: String) {
        db.appSettingsDao().set(AppSettingEntity(SESSION_USER_ID_KEY, userId))
    }

    override suspend fun changePasscode(userId: String, newPasscode: String) {
        db.userDao().changePasscode(userId, SecurityHasher.hashPasscode(newPasscode))
        saveSession(userId)
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
        val company = Company(name = name.trim().ifBlank { "شركة بدون اسم" })
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
        db.withTransaction {
            db.companyDao().updateTier(companyId, tier.name)
            adjustCompanyVisitsForTier(companyId, tier)
        }
        afterMutation("tier")
    }

    private suspend fun adjustCompanyVisitsForTier(companyId: String, tier: Tier) {
        val start = cycleStart()
        val currentEpoch = start.toEpochDay()
        val expected = tier.visitsPerCycle
        val allVisits = db.visitDao().listCycle(currentEpoch).map { it.toDomain() }
        val companyVisits = allVisits
            .filter { it.companyId == companyId }
            .sortedWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.date }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })

        when {
            companyVisits.size > expected -> {
                val deleteIds = companyVisits.takeLast(companyVisits.size - expected).map { it.id }
                if (deleteIds.isNotEmpty()) db.visitDao().softDeleteByIds(deleteIds)
            }
            companyVisits.size < expected -> {
                val additions = mutableListOf<Visit>()
                repeat(expected - companyVisits.size) { index ->
                    val occupied = allVisits + additions
                    val slot = chooseStableInsertionSlot(start, companyId, occupied)
                    val dayOfCycle = cycleCalculator.dayOfCycle(start, slot.date)
                    additions += Visit(
                        id = UUID.nameUUIDFromBytes("$companyId-$currentEpoch-added-${slot.date}-${slot.shift}-${slot.slotIndex}-${companyVisits.size + index + 1}".toByteArray()).toString(),
                        companyId = companyId,
                        cycleStartEpochDay = currentEpoch,
                        dayOfCycle = dayOfCycle,
                        weekOfCycle = cycleCalculator.weekOfCycle(dayOfCycle),
                        date = slot.date,
                        shift = slot.shift,
                        slotIndex = slot.slotIndex,
                    )
                }
                if (additions.isNotEmpty()) db.visitDao().upsertAll(additions.map { it.toEntity() })
            }
        }
    }

    private fun chooseStableInsertionSlot(
        cycleStart: LocalDate,
        companyId: String,
        occupied: List<Visit>,
    ): StableSlot {
        val candidates = cycleCalculator.workingDatesInCycle(cycleStart).flatMap { date ->
            Shift.entries.mapNotNull { shift ->
                if (occupied.any { it.companyId == companyId && it.date == date }) return@mapNotNull null
                val visitsInShift = occupied.filter { it.date == date && it.shift == shift }
                if (visitsInShift.size >= 10) return@mapNotNull null
                val preferredMax = if (shift == Shift.MORNING) 7 else 8
                val preferredSlot = firstVacantSlot(visitsInShift.map { it.slotIndex }.toSet(), preferredMax)
                val overflowSlot = firstVacantSlot(visitsInShift.map { it.slotIndex }.toSet(), 10)
                val slotIndex = preferredSlot ?: overflowSlot ?: return@mapNotNull null
                val overflowPenalty = if (preferredSlot == null) 10_000 else 0
                StableSlot(
                    date = date,
                    shift = shift,
                    slotIndex = slotIndex,
                    score = overflowPenalty + (visitsInShift.size * 100) + slotIndex + (if (shift == Shift.MORNING) 5 else 0),
                )
            }
        }
        return candidates.minWithOrNull(compareBy<StableSlot> { it.score }.thenBy { it.date }.thenBy { it.shift.ordinal })
            ?: error("لا توجد مساحة متاحة لإضافة زيارة جديدة حتى سعة 10 شركات في الفترة")
    }

    private fun firstVacantSlot(used: Set<Int>, maxSlot: Int): Int? = (1..maxSlot).firstOrNull { it !in used }

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

            if (ensureCurrentCycleSchedule()) {
                val generatedVisits = db.visitDao().dirty()
                syncService.pushVisits(generatedVisits)
                if (generatedVisits.isNotEmpty()) db.visitDao().markClean(generatedVisits.map { it.id })
                backupService.dumpDatabase("cycle_rotation")
            }
        } catch (throwable: Throwable) {
            Log.w("BorgSync", "Cloud sync skipped/failed; local cache remains authoritative", throwable)
        }
    }

    private suspend fun ensureCurrentCycleSchedule(): Boolean {
        val start = cycleStart()
        val currentEpoch = start.toEpochDay()
        if (db.visitDao().listCycle(currentEpoch).isNotEmpty()) return false

        val companies = db.companyDao().search("").map { it.toDomain() }.filter { it.tier.visitsPerCycle > 0 }
        if (companies.isEmpty()) return false

        val templateEpoch = db.visitDao().latestCycleBefore(currentEpoch)
        val candidateVisits = if (templateEpoch != null) {
            val activeCompanyIds = companies.map { it.id }.toSet()
            db.visitDao().listCycle(templateEpoch)
                .map { it.toDomain() }
                .filter { it.companyId in activeCompanyIds }
                .map { template ->
                    val date = start.plusDays((template.dayOfCycle - 1).toLong())
                    template.copy(
                        id = UUID.nameUUIDFromBytes("${template.companyId}-$currentEpoch-${template.dayOfCycle}-${template.shift}-${template.slotIndex}".toByteArray()).toString(),
                        cycleStartEpochDay = currentEpoch,
                        date = date,
                        status = VisitStatus.SCHEDULED,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        deletedAt = null,
                    )
                }
        } else {
            emptyList()
        }

        val plan = scheduleGenerator.reconcile(start, companies, candidateVisits)
        val deletedIds = plan.visitsToSoftDelete.map { it.id }.toSet()
        val finalVisits = candidateVisits.filterNot { it.id in deletedIds } + plan.visitsToUpsert
        if (finalVisits.isEmpty()) return false
        db.visitDao().upsertAll(finalVisits.map { it.toEntity() })
        return true
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
