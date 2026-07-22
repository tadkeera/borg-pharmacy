package com.borgpharmacy.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.borgpharmacy.backup.BackupService
import com.borgpharmacy.data.local.AppSettingEntity
import com.borgpharmacy.data.local.BorgDatabase
import com.borgpharmacy.data.local.CompanyEntity
import com.borgpharmacy.data.local.DEFAULT_TENANT_ID
import com.borgpharmacy.data.local.PrintLogEntity
import com.borgpharmacy.data.local.RepresentativeEntity
import com.borgpharmacy.data.local.TierCountTuple
import com.borgpharmacy.data.local.UserEntity
import com.borgpharmacy.data.local.VisitEntity
import com.borgpharmacy.data.local.toDomain
import com.borgpharmacy.data.local.toEntity
import com.borgpharmacy.data.remote.SupabaseClientProvider
import com.borgpharmacy.data.remote.SupabaseSyncService
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.CompanyReportScore
import com.borgpharmacy.domain.CycleCalculator
import com.borgpharmacy.domain.PrintCount
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.RepresentativeInquiryReport
import com.borgpharmacy.domain.ScheduleGenerator
import com.borgpharmacy.domain.SchedulePlan
import com.borgpharmacy.domain.Tier
import com.borgpharmacy.domain.UserAccount
import com.borgpharmacy.domain.UserRole
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.VisitStatus
import com.borgpharmacy.security.SecurityHasher
import com.borgpharmacy.ui.screens.BotLog
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

@Serializable
data class BotConfigDto(
    val id: String = "primary_bot",
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class BotLogDto(
    val id: String? = null,
    @SerialName("sender_phone") val senderPhone: String,
    @SerialName("query_text") val queryText: String,
    @SerialName("matched_company") val matchedCompany: String,
    @SerialName("created_at") val createdAt: String? = null,
)


@Serializable
data class RepresentativePortalReportDto(
    @SerialName("representative_id") val representativeId: String,
    @SerialName("representative_name") val representativeName: String,
    @SerialName("representative_phone") val representativePhone: String,
    @SerialName("company_id") val companyId: String,
    @SerialName("company_name") val companyName: String,
    @SerialName("search_count") val searchCount: Int = 0,
    @SerialName("first_search_at") val firstSearchAt: String? = null,
    @SerialName("last_search_at") val lastSearchAt: String? = null,
)

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
    suspend fun updateCompanyTiers(changes: Map<String, Tier>)
    suspend fun updateCompanyName(companyId: String, name: String)
    suspend fun deleteCompany(companyId: String)
    suspend fun deleteAllCompanies()
    suspend fun addRepresentative(companyId: String, name: String, phone: String): Representative
    suspend fun deleteRepresentative(repId: String)
    suspend fun setVisitStatus(visitId: String, status: VisitStatus)
    suspend fun recordPrint(repId: String, visitId: String)
    suspend fun rescheduleCurrentCycle()
    suspend fun syncNow()
    suspend fun backupNow(reason: String = "manual")
    suspend fun dashboardScores(): List<CompanyReportScore>

    suspend fun fetchBotConfig(): Pair<String, Boolean>
    suspend fun saveBotConfig(phoneNumber: String, isActive: Boolean)
    suspend fun fetchBotLogs(): List<BotLog>
    suspend fun fetchRepresentativeInquiryReports(): List<RepresentativeInquiryReport>
}

private const val SESSION_USER_ID_KEY = "session_user_id"
private const val AUTH_ACCESS_TOKEN_KEY = "auth_access_token"
private const val AUTH_REFRESH_TOKEN_KEY = "auth_refresh_token"
private const val AUTH_TENANT_ID_KEY = "auth_tenant_id"

class OfflineFirstBorgRepository(
    private val db: BorgDatabase,
    private val backupService: BackupService,
    private val syncService: SupabaseSyncService,
    private val scheduleGenerator: ScheduleGenerator = ScheduleGenerator(),
    private val cycleCalculator: CycleCalculator = CycleCalculator(),
) : BorgRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun getActiveTenantId(): String =
        db.appSettingsDao().getValue(AUTH_TENANT_ID_KEY)?.takeIf { it.isNotBlank() } ?: DEFAULT_TENANT_ID

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
        // إصلاح محلي فوري قبل ظهور الواجهة: يمنع عرض زيارات يتيمة أو مكررة بعد ترقيات المزامنة.
        runCatching { ensureCurrentCycleSchedule() }
            .onFailure { throwable -> Log.w("BorgInit", "Initial local schedule repair failed", throwable) }
        scope.launch { syncNow() }
        return start
    }

    override suspend fun cycleStart(): LocalDate {
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
        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.isBlank() || passcode.isBlank()) return null

        val passcodeHash = SecurityHasher.hashPasscode(passcode)

        // المرحلة الجديدة: تسجيل الدخول عبر Supabase Auth أولاً.
        val authUser = runCatching {
            val session = syncService.signInWithPassword(cleanUsername, passcode)
            val profile = syncService.fetchProfile(session.accessToken, session.userId)
                ?: error("لم يتم العثور على ملف صلاحيات المستخدم في user_profiles")
            if (!profile.active) error("هذا المستخدم غير مفعل")

            val entity = UserEntity(
                id = session.userId,
                tenantId = profile.tenantId,
                username = session.email.ifBlank { cleanUsername },
                displayName = profile.displayName.ifBlank { session.email.ifBlank { cleanUsername } },
                role = if (profile.role == UserRole.ADMIN.name) UserRole.ADMIN.name else UserRole.PHARMACIST.name,
                passcodeHash = passcodeHash,
                mustChangePasscode = profile.mustChangePassword,
                active = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = com.borgpharmacy.data.local.SyncStatus.SYNCED.name,
                isDeleted = false,
            )
            db.userDao().upsert(entity)
            saveAuthSession(entity.id, session.accessToken, session.refreshToken, profile.tenantId)
            entity
        }.onFailure { throwable ->
            Log.w("BorgLogin", "Supabase Auth login failed; falling back to legacy login", throwable)
        }.getOrNull()

        if (authUser != null) return authUser.toDomain()

        // احتياطي مؤقت حتى يتم اكتمال نقل كل الأجهزة إلى Supabase Auth.
        var user = db.userDao().findByUsername(cleanUsername)
        if (user == null || !SecurityHasher.verify(passcode, user.passcodeHash)) {
            val remoteUser = runCatching { syncService.loginUser(cleanUsername, passcodeHash) }
                .onFailure { throwable -> Log.w("BorgLogin", "Legacy cloud login check failed; falling back to full sync", throwable) }
                .getOrNull()
            if (remoteUser != null) {
                db.userDao().upsert(remoteUser)
                user = remoteUser
            } else {
                syncNow()
                user = db.userDao().findByUsername(cleanUsername)
            }
        }

        val validUser = user ?: return null
        if (!SecurityHasher.verify(passcode, validUser.passcodeHash)) return null
        if (!validUser.mustChangePasscode) saveSession(validUser.id)
        return validUser.toDomain()
    }

    override suspend fun restoreSavedSession(): UserAccount? {
        val userId = db.appSettingsDao().getValue(SESSION_USER_ID_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val user = db.userDao().getById(userId) ?: return null
        return if (user.mustChangePasscode) null else user.toDomain()
    }

    override suspend fun clearSavedSession() {
        db.appSettingsDao().set(AppSettingEntity(SESSION_USER_ID_KEY, ""))
        db.appSettingsDao().set(AppSettingEntity(AUTH_ACCESS_TOKEN_KEY, ""))
        db.appSettingsDao().set(AppSettingEntity(AUTH_REFRESH_TOKEN_KEY, ""))
        db.appSettingsDao().set(AppSettingEntity(AUTH_TENANT_ID_KEY, ""))
    }

    private suspend fun saveSession(userId: String) {
        db.appSettingsDao().set(AppSettingEntity(SESSION_USER_ID_KEY, userId))
    }

    private suspend fun saveAuthSession(userId: String, accessToken: String, refreshToken: String, tenantId: String) {
        saveSession(userId)
        db.appSettingsDao().set(AppSettingEntity(AUTH_ACCESS_TOKEN_KEY, accessToken))
        db.appSettingsDao().set(AppSettingEntity(AUTH_REFRESH_TOKEN_KEY, refreshToken))
        db.appSettingsDao().set(AppSettingEntity(AUTH_TENANT_ID_KEY, tenantId))
    }

    override suspend fun changePasscode(userId: String, newPasscode: String) {
        db.userDao().changePasscode(userId, SecurityHasher.hashPasscode(newPasscode))
        saveSession(userId)
        db.userDao().getById(userId)?.let { user ->
            runCatching { syncService.pushUsers(listOf(user)) }
                .onFailure { throwable -> Log.w("BorgSync", "Immediate passcode sync failed", throwable) }
        }
        afterMutation("passcode")
    }

    override suspend fun createUser(username: String, displayName: String, role: UserRole, passcode: String) {
        val cleanEmail = username.trim().lowercase()
        if (cleanEmail.isBlank()) return
        val cleanDisplayName = displayName.trim().ifBlank { cleanEmail }
        val passcodeHash = SecurityHasher.hashPasscode(passcode)
        val accessToken = db.appSettingsDao().getValue(AUTH_ACCESS_TOKEN_KEY).orEmpty()

        // المرحلة الجديدة: إنشاء المستخدم في Supabase Auth عبر Edge Function آمنة يستدعيها الأدمن فقط.
        val authCreated = if (accessToken.isNotBlank()) {
            runCatching {
                syncService.adminCreateAuthUser(
                    accessToken = accessToken,
                    email = cleanEmail,
                    password = passcode,
                    displayName = cleanDisplayName,
                    role = role.name,
                )
            }.onFailure { throwable ->
                Log.w("BorgAuth", "Admin Auth user creation failed; using legacy fallback", throwable)
            }.getOrNull()
        } else {
            null
        }

        val entity = if (authCreated != null) {
            UserEntity(
                id = authCreated.id,
                tenantId = authCreated.tenantId,
                username = authCreated.email.trim().lowercase(),
                displayName = authCreated.displayName.ifBlank { cleanDisplayName },
                role = if (authCreated.role == UserRole.ADMIN.name) UserRole.ADMIN.name else UserRole.PHARMACIST.name,
                passcodeHash = passcodeHash,
                mustChangePasscode = false,
                active = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = com.borgpharmacy.data.local.SyncStatus.SYNCED.name,
                isDeleted = false,
            )
        } else {
            UserEntity(
                username = cleanEmail,
                displayName = cleanDisplayName,
                role = role.name,
                passcodeHash = passcodeHash,
                mustChangePasscode = false,
            )
        }

        db.userDao().upsert(entity)
        runCatching { syncService.pushUsers(listOf(entity)) }
            .onFailure { throwable -> Log.w("BorgSync", "Immediate user sync failed", throwable) }
        afterMutation("user")
    }

    override suspend fun addCompany(name: String): Company {
        val company = Company(name = name.trim().ifBlank { "شركة بدون اسم" })
        db.withTransaction {
            db.companyDao().upsert(company.toEntity())
            val start = cycleStart()
            val visits = db.visitDao().listCycle(start.toEpochDay()).map { it.toDomain() }
            val plan = scheduleGenerator.reconcileSingleCompany(start, company, visits)
            applySchedulePlan(plan)
            persistBaseSlotsFromVisits(plan.visitsToUpsert)
            repairCurrentCycleLocked(start)
        }
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
            db.withTransaction {
                db.companyDao().upsertAll(rows)
                val start = cycleStart()
                var workingVisits = db.visitDao().listCycle(start.toEpochDay()).map { it.toDomain() }
                val allUpserts = mutableListOf<Visit>()
                rows.forEach { row ->
                    val plan = scheduleGenerator.reconcileSingleCompany(start, row.toDomain(), workingVisits)
                    applySchedulePlan(plan)
                    persistBaseSlotsFromVisits(plan.visitsToUpsert)
                    workingVisits = workingVisits + plan.visitsToUpsert
                    allUpserts += plan.visitsToUpsert
                }
                persistBaseSlotsFromVisits(allUpserts)
                repairCurrentCycleLocked(start)
            }
            afterMutation("csv_import")
        }
        return rows.size
    }

    override suspend fun updateCompanyTier(companyId: String, tier: Tier) {
        updateCompanyTiers(mapOf(companyId to tier))
    }

    override suspend fun updateCompanyTiers(changes: Map<String, Tier>) {
        val normalized = changes.filterKeys { it.isNotBlank() }
        if (normalized.isEmpty()) return

        val start = cycleStart()
        db.withTransaction {
            var workingVisits = db.visitDao().listCycle(start.toEpochDay()).map { it.toDomain() }
            val accumulatedDeletes = mutableListOf<Visit>()
            val accumulatedUpserts = mutableListOf<Visit>()

            normalized.forEach { (companyId, newTier) ->
                val entity = db.companyDao().getById(companyId) ?: return@forEach
                val oldTier = Tier.fromString(entity.tier)
                if (oldTier == newTier) return@forEach

                db.companyDao().updateTier(companyId, newTier.name)
                val company = entity.toDomain().copy(tier = newTier)
                val plan = scheduleGenerator.reconcileSingleCompany(start, company, workingVisits)
                val deleteIds = plan.visitsToSoftDelete.map { it.id }.toSet()

                accumulatedDeletes += plan.visitsToSoftDelete
                accumulatedUpserts += plan.visitsToUpsert
                workingVisits = workingVisits.filterNot { it.id in deleteIds } + plan.visitsToUpsert
            }

            applySchedulePlan(
                SchedulePlan(
                    visitsToUpsert = accumulatedUpserts.distinctBy { it.id },
                    visitsToSoftDelete = accumulatedDeletes.distinctBy { it.id },
                )
            )
        }
        afterMutation("tier_batch")
    }

    override suspend fun updateCompanyName(companyId: String, name: String) {
        val cleanName = name.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSurrounding("“", "”")
            .trim()
        if (cleanName.isBlank()) return
        db.companyDao().updateName(companyId, cleanName)
        afterMutation("company_name")
    }

    private suspend fun applySchedulePlan(plan: SchedulePlan) {
        val deleteIds = plan.visitsToSoftDelete.map { it.id }
        if (deleteIds.isNotEmpty()) db.visitDao().softDeleteByIds(deleteIds)
        if (plan.visitsToUpsert.isNotEmpty()) db.visitDao().upsertAll(plan.visitsToUpsert.map { it.toEntity() })
    }

    private suspend fun persistBaseSlotsFromVisits(visits: List<Visit>) {
        visits
            .filter { it.weekOfCycle == 1 }
            .groupBy { it.companyId }
            .forEach { (companyId, companyVisits) ->
                val visit = companyVisits.minWithOrNull(compareBy<Visit> { it.date }.thenBy { it.shift.ordinal }) ?: return@forEach
                val baseDayIndex = ((visit.dayOfCycle - 1) % 7).coerceIn(0, 4)
                db.companyDao().updateBaseSlot(companyId, baseDayIndex, visit.shift.name)
            }
    }

    private suspend fun persistMissingBaseSlots(start: LocalDate) {
        val companies = db.companyDao().listActive().filter { it.baseDayIndex == null || it.baseShift == null }
        if (companies.isEmpty()) return
        val visitsByCompany = db.visitDao().listCycle(start.toEpochDay()).map { it.toDomain() }.groupBy { it.companyId }
        companies.forEach { company ->
            val visit = visitsByCompany[company.id]
                ?.filter { it.weekOfCycle == 1 }
                ?.minWithOrNull(compareBy<Visit> { it.date }.thenBy { it.shift.ordinal })
                ?: visitsByCompany[company.id]?.minWithOrNull(compareBy<Visit> { it.weekOfCycle }.thenBy { it.date }.thenBy { it.shift.ordinal })
                ?: return@forEach
            val inferredDay = Math.floorMod((((visit.dayOfCycle - 1) % 7).coerceIn(0, 4)) - (visit.weekOfCycle - 1), 5)
            val inferredShift = if (visit.weekOfCycle == 2 || visit.weekOfCycle == 4) {
                if (visit.shift == com.borgpharmacy.domain.Shift.MORNING) com.borgpharmacy.domain.Shift.EVENING else com.borgpharmacy.domain.Shift.MORNING
            } else {
                visit.shift
            }
            db.companyDao().updateBaseSlot(company.id, inferredDay, inferredShift.name)
        }
    }

    private suspend fun repairCurrentCycleLocked(start: LocalDate): Boolean {
        persistMissingBaseSlots(start)
        var changed = false
        var workingVisits = db.visitDao().listCycle(start.toEpochDay()).map { it.toDomain() }
        val companies = db.companyDao().listActive().map { it.toDomain() }
        companies.forEach { company ->
            val activeCount = workingVisits.count { it.companyId == company.id }
            if (activeCount == 4 && company.baseDayIndex != null && company.baseShift != null) return@forEach
            val plan = scheduleGenerator.reconcileSingleCompany(start, company, workingVisits)
            if (plan.visitsToUpsert.isNotEmpty() || plan.visitsToSoftDelete.isNotEmpty()) {
                applySchedulePlan(plan)
                persistBaseSlotsFromVisits(plan.visitsToUpsert)
                val deleteIds = plan.visitsToSoftDelete.map { it.id }.toSet()
                workingVisits = workingVisits.filterNot { it.id in deleteIds } + plan.visitsToUpsert
                changed = true
            }
        }
        persistMissingBaseSlots(start)
        return changed
    }

    override suspend fun deleteCompany(companyId: String) {
        db.withTransaction {
            db.companyDao().softDelete(companyId)
            db.visitDao().softDeleteForCompany(companyId)
            repairCurrentCycleLocked(cycleStart())
        }
        afterMutation("company_delete")
    }

    override suspend fun deleteAllCompanies() {
        val tenantId = getActiveTenantId()
        db.withTransaction {
            val timestamp = System.currentTimeMillis()
            db.companyDao().softDeleteAllForTenant(tenantId, timestamp)
            db.representativeDao().softDeleteAllForTenant(tenantId, timestamp)
            db.visitDao().softDeleteAllForTenant(tenantId, timestamp)
            setCatalogReplacePending(true)
        }
        afterMutation("company_delete_all")
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
        val companies = db.companyDao().listActive().map { it.toDomain() }
        val visits = db.visitDao().listCycle(start.toEpochDay()).map { it.toDomain() }
        val plan = scheduleGenerator.reconcile(start, companies, visits)
        db.withTransaction {
            applySchedulePlan(plan)
            persistBaseSlotsFromVisits(plan.visitsToUpsert)
            repairCurrentCycleLocked(start)
        }
        afterMutation("schedule")
    }

    override suspend fun syncNow() {
        val activeTenantId = getActiveTenantId()
        val replacePending = isCatalogReplacePending()

        val companies = db.companyDao().dirtyForTenant(activeTenantId)
        val reps = db.representativeDao().dirtyForTenant(activeTenantId)
        val visits = db.visitDao().dirtyForTenant(activeTenantId)
        val users = db.userDao().listAllForTenant(activeTenantId)

        runCatching {
            syncService.pushCompanies(companies)
            if (companies.isNotEmpty()) db.companyDao().markClean(companies.map { it.id })
        }.onFailure { throwable ->
            Log.w("BorgSync", "Company push failed", throwable)
        }

        runCatching {
            syncService.pushRepresentatives(reps)
            if (reps.isNotEmpty()) db.representativeDao().markClean(reps.map { it.id })
        }.onFailure { throwable ->
            Log.w("BorgSync", "Representative push failed", throwable)
        }

        runCatching {
            syncService.pushVisits(visits)
            if (visits.isNotEmpty()) db.visitDao().markClean(visits.map { it.id })
        }.onFailure { throwable ->
            Log.w("BorgSync", "Visit push failed", throwable)
        }

        runCatching {
            syncService.pushUsers(users)
        }.onFailure { throwable ->
            Log.w("BorgSync", "User push failed", throwable)
        }

        if (replacePending) {
            val activeCompanyIds = db.companyDao().activeIdsForTenant(activeTenantId)
            runCatching {
                syncService.pruneTenantToActiveCompanies(activeTenantId, activeCompanyIds)
                setCatalogReplacePending(false)
            }.onFailure { throwable ->
                Log.w("BorgSync", "Remote catalog prune failed; replacement flag kept for next sync", throwable)
            }
        }

        val remote = runCatching { syncService.pullAll(activeTenantId) }
            .onFailure { throwable -> Log.w("BorgSync", "Cloud pull failed; local cache remains authoritative", throwable) }
            .getOrNull()
            ?: return

        db.withTransaction {
            if (remote.companies.isNotEmpty()) {
                val mergedCompanies = remote.companies.mapNotNull { remoteCompany ->
                    val local = db.companyDao().getById(remoteCompany.id)
                    if (local != null && local.dirty && local.updatedAt > remoteCompany.updatedAt) {
                        null
                    } else {
                        remoteCompany.copy(
                            baseDayIndex = remoteCompany.baseDayIndex ?: local?.baseDayIndex,
                            baseShift = remoteCompany.baseShift ?: local?.baseShift,
                        )
                    }
                }
                if (mergedCompanies.isNotEmpty()) db.companyDao().upsertAll(mergedCompanies)
            }
            if (remote.representatives.isNotEmpty()) db.representativeDao().upsertAll(remote.representatives)
            if (remote.visits.isNotEmpty()) db.visitDao().upsertAll(remote.visits)
            if (remote.users.isNotEmpty()) db.userDao().upsertAll(remote.users)

            // اجعل Room مرآة للسحابة بعد المزامنة: أي شركة نظيفة محلياً ولم تعد نشطة في السحابة تُزال.
            // لا نحذف السجلات dirty حتى لا نخسر تعديلات Offline لم تُرفع بعد.
            val remoteActiveCompanyIds = remote.companies.map { it.id }
            if (remoteActiveCompanyIds.isNotEmpty()) {
                db.companyDao().purgeSyncedActiveNotInForTenant(activeTenantId, remoteActiveCompanyIds)
            }
            purgeInvalidLocalRows(activeTenantId)
        }

        if (ensureCurrentCycleSchedule()) {
            val generatedVisits = db.visitDao().dirtyForTenant(activeTenantId)
            runCatching {
                syncService.pushVisits(generatedVisits)
                if (generatedVisits.isNotEmpty()) db.visitDao().markClean(generatedVisits.map { it.id })
            }.onFailure { throwable ->
                Log.w("BorgSync", "Generated visits push failed", throwable)
            }
        }
    }

    private suspend fun isCatalogReplacePending(): Boolean =
        db.appSettingsDao().getValue("catalog_replace_pending") == "1"

    private suspend fun setCatalogReplacePending(pending: Boolean) {
        db.appSettingsDao().set(AppSettingEntity("catalog_replace_pending", if (pending) "1" else "0"))
    }

    private suspend fun purgeInvalidLocalRows(tenantId: String) {
        db.visitDao().purgeDeletedAndOrphansForTenant(tenantId)
        db.representativeDao().purgeDeletedAndOrphansForTenant(tenantId)
        db.companyDao().purgeDeletedForTenant(tenantId)
    }

    private suspend fun ensureCurrentCycleSchedule(): Boolean {
        val start = cycleStart()
        val currentEpoch = start.toEpochDay()
        val companies = db.companyDao().listActive().map { it.toDomain() }
        if (companies.isEmpty()) return false

        val currentVisits = db.visitDao().listCycle(currentEpoch).map { it.toDomain() }
        if (currentVisits.isNotEmpty()) {
            return repairCurrentCycleLocked(start)
        }

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
        if (plan.visitsToUpsert.isEmpty() && plan.visitsToSoftDelete.isEmpty()) {
            persistMissingBaseSlots(start)
            return false
        }
        applySchedulePlan(plan)
        persistBaseSlotsFromVisits(plan.visitsToUpsert)
        persistMissingBaseSlots(start)
        return true
    }

    override suspend fun backupNow(reason: String) {
        backupService.dumpDatabase(reason)
    }

    override suspend fun dashboardScores(): List<CompanyReportScore> {
        val companies = db.companyDao().listActive().map { it.toDomain() }
        val visits = db.visitDao().listCycle(cycleStart().toEpochDay()).map { it.toDomain() }
        return companies.map { company ->
            val expected = 4
            val completed = visits.count { it.companyId == company.id && it.status == VisitStatus.COMPLETED }
            val score = (completed.toDouble() / expected.toDouble()) * 10.0
            CompanyReportScore(company, expected, completed, score.coerceAtMost(10.0))
        }
    }

    // 🟢 فرض استخدام Dispatchers.IO بشكل صارم لتجنب أي تعليق في الواجهة الرئيسية
    override suspend fun fetchBotConfig(): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        try {
            val configs = SupabaseClientProvider.client
                .from("bot_config")
                .select()
                .decodeList<BotConfigDto>()
            val config = configs.firstOrNull { it.id == "primary_bot" } ?: configs.firstOrNull()
            (config?.phoneNumber ?: "967") to (config?.isActive ?: false)
        } catch (throwable: Throwable) {
            Log.w("BorgBot", "Unable to fetch bot_config from Supabase", throwable)
            "967" to false
        }
    }

    override suspend fun saveBotConfig(phoneNumber: String, isActive: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val normalizedPhone = phoneNumber.filter { it.isDigit() }.ifBlank { "967" }
                SupabaseClientProvider.client
                    .from("bot_config")
                    .upsert(BotConfigDto(phoneNumber = normalizedPhone, isActive = isActive))
                Unit
            } catch (throwable: Throwable) {
                Log.e("BorgBot", "Unable to save bot_config to Supabase", throwable)
                throw throwable
            }
        }
    }

    override suspend fun fetchBotLogs(): List<BotLog> = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client
                .from("bot_logs")
                .select()
                .decodeList<BotLogDto>()
                .sortedByDescending { it.createdAt.orEmpty() }
                .map { dto ->
                    BotLog(
                        id = dto.id.orEmpty(),
                        senderPhone = dto.senderPhone,
                        queryText = dto.queryText,
                        matchedCompany = dto.matchedCompany,
                        createdAt = dto.createdAt?.take(16)?.replace("T", " ").orEmpty(),
                    )
                }
        } catch (throwable: Throwable) {
            Log.w("BorgBot", "Unable to fetch bot_logs from Supabase", throwable)
            emptyList()
        }
    }

    override suspend fun fetchRepresentativeInquiryReports(): List<RepresentativeInquiryReport> = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client
                .from("representative_portal_report")
                .select()
                .decodeList<RepresentativePortalReportDto>()
                .sortedByDescending { it.lastSearchAt.orEmpty() }
                .map { dto ->
                    RepresentativeInquiryReport(
                        representativeId = dto.representativeId,
                        representativeName = dto.representativeName,
                        representativePhone = dto.representativePhone,
                        companyId = dto.companyId,
                        companyName = dto.companyName,
                        searchCount = dto.searchCount,
                        firstSearchAt = dto.firstSearchAt?.take(16)?.replace("T", " ").orEmpty(),
                        lastSearchAt = dto.lastSearchAt?.take(16)?.replace("T", " ").orEmpty(),
                    )
                }
        } catch (throwable: Throwable) {
            Log.w("BorgPortal", "Unable to fetch representative portal report", throwable)
            emptyList()
        }
    }

    private fun UserEntity.isUnchangedSeedAdmin(): Boolean =
        username.equals("admin", ignoreCase = true) &&
            mustChangePasscode &&
            passcodeHash == SecurityHasher.hashPasscode("admin2026")

    private fun normalizePhone(input: String): String {
        val trimmed = input.trim().ifBlank { "+967" }
        return if (trimmed.startsWith("+")) trimmed else "+967$trimmed"
    }

    private fun afterMutation(reason: String) {
        scope.launch { syncNow() }
    }
}