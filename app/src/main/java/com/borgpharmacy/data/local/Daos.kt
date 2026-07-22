package com.borgpharmacy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActiveForTenant(tenantId: String): Flow<List<CompanyEntity>>
    fun observeActive(): Flow<List<CompanyEntity>> = observeActiveForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT * FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    suspend fun listActiveForTenant(tenantId: String): List<CompanyEntity>
    suspend fun listActive(): List<CompanyEntity> = listActiveForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL")
    suspend fun activeIdsForTenant(tenantId: String): List<String>

    @Query("SELECT * FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE LIMIT 50")
    suspend fun searchForTenant(tenantId: String, query: String): List<CompanyEntity>
    suspend fun search(query: String): List<CompanyEntity> = searchForTenant(DEFAULT_TENANT_ID, query)

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CompanyEntity?

    @Query("SELECT * FROM companies WHERE tenantId = :tenantId AND (dirty = 1 OR syncStatus != 'SYNCED')")
    suspend fun dirtyForTenant(tenantId: String): List<CompanyEntity>
    suspend fun dirty(): List<CompanyEntity> = dirtyForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM companies WHERE tenantId = :tenantId")
    suspend fun maxUpdatedAt(tenantId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompanyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CompanyEntity>)

    @Query("UPDATE companies SET tier = :tier, updatedAt = :updatedAt, dirty = 1, syncStatus = 'PENDING' WHERE id = :companyId")
    suspend fun updateTier(companyId: String, tier: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET name = :name, updatedAt = :updatedAt, dirty = 1, syncStatus = 'PENDING' WHERE id = :companyId")
    suspend fun updateName(companyId: String, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET baseDayIndex = :baseDayIndex, baseShift = :baseShift, updatedAt = :updatedAt, dirty = 1, syncStatus = 'PENDING' WHERE id = :companyId")
    suspend fun updateBaseSlot(companyId: String, baseDayIndex: Int, baseShift: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1, syncStatus = 'PENDING', isDeleted = 1 WHERE id = :companyId")
    suspend fun softDelete(companyId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1, syncStatus = 'PENDING', isDeleted = 1 WHERE tenantId = :tenantId AND isDeleted = 0")
    suspend fun softDeleteAllForTenant(tenantId: String, deletedAt: Long = System.currentTimeMillis())
    suspend fun softDeleteAll(deletedAt: Long = System.currentTimeMillis()) = softDeleteAllForTenant(DEFAULT_TENANT_ID, deletedAt)

    @Query("UPDATE companies SET dirty = 0, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markClean(ids: List<String>)

    @Query("UPDATE companies SET syncStatus = 'FAILED' WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<String>)

    @Query("DELETE FROM companies WHERE tenantId = :tenantId AND dirty = 0 AND (isDeleted = 1 OR deletedAt IS NOT NULL)")
    suspend fun purgeDeletedForTenant(tenantId: String)

    @Query("DELETE FROM companies WHERE tenantId = :tenantId AND dirty = 0 AND isDeleted = 0 AND deletedAt IS NULL AND id NOT IN (:keepIds)")
    suspend fun purgeSyncedActiveNotInForTenant(tenantId: String, keepIds: List<String>)

    @Query("SELECT tier, COUNT(*) AS count FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL GROUP BY tier")
    fun observeTierCountsForTenant(tenantId: String): Flow<List<TierCountTuple>>
    fun observeTierCounts(): Flow<List<TierCountTuple>> = observeTierCountsForTenant(DEFAULT_TENANT_ID)
}

@Dao
interface RepresentativeDao {
    @Query("SELECT * FROM representatives WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL AND companyId IN (SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL) ORDER BY name COLLATE NOCASE")
    fun observeActiveForTenant(tenantId: String): Flow<List<RepresentativeEntity>>
    fun observeActive(): Flow<List<RepresentativeEntity>> = observeActiveForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT * FROM representatives WHERE tenantId = :tenantId AND companyId = :companyId AND isDeleted = 0 AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeForCompanyAndTenant(tenantId: String, companyId: String): Flow<List<RepresentativeEntity>>
    fun observeForCompany(companyId: String): Flow<List<RepresentativeEntity>> = observeForCompanyAndTenant(DEFAULT_TENANT_ID, companyId)

    @Query("SELECT * FROM representatives WHERE tenantId = :tenantId AND (dirty = 1 OR syncStatus != 'SYNCED')")
    suspend fun dirtyForTenant(tenantId: String): List<RepresentativeEntity>
    suspend fun dirty(): List<RepresentativeEntity> = dirtyForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM representatives WHERE tenantId = :tenantId")
    suspend fun maxUpdatedAt(tenantId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RepresentativeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RepresentativeEntity>)

    @Query("UPDATE representatives SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1, syncStatus = 'PENDING', isDeleted = 1 WHERE id = :repId")
    suspend fun softDelete(repId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE representatives SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1, syncStatus = 'PENDING', isDeleted = 1 WHERE tenantId = :tenantId AND isDeleted = 0")
    suspend fun softDeleteAllForTenant(tenantId: String, deletedAt: Long = System.currentTimeMillis())
    suspend fun softDeleteAll(deletedAt: Long = System.currentTimeMillis()) = softDeleteAllForTenant(DEFAULT_TENANT_ID, deletedAt)

    @Query("UPDATE representatives SET dirty = 0, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markClean(ids: List<String>)

    @Query("UPDATE representatives SET syncStatus = 'FAILED' WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<String>)

    @Query("DELETE FROM representatives WHERE tenantId = :tenantId AND dirty = 0 AND (isDeleted = 1 OR deletedAt IS NOT NULL OR companyId NOT IN (SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL))")
    suspend fun purgeDeletedAndOrphansForTenant(tenantId: String)
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL AND companyId IN (SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL) ORDER BY dateEpochDay, shift, slotIndex")
    fun observeActiveForTenant(tenantId: String): Flow<List<VisitEntity>>
    fun observeActive(): Flow<List<VisitEntity>> = observeActiveForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT * FROM visits WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL AND cycleStartEpochDay = :cycleStartEpochDay AND companyId IN (SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL) ORDER BY dateEpochDay, shift, slotIndex")
    fun observeCycleForTenant(tenantId: String, cycleStartEpochDay: Long): Flow<List<VisitEntity>>
    fun observeCycle(cycleStartEpochDay: Long): Flow<List<VisitEntity>> = observeCycleForTenant(DEFAULT_TENANT_ID, cycleStartEpochDay)

    @Query("SELECT * FROM visits WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL AND dateEpochDay = :dateEpochDay AND companyId IN (SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL) ORDER BY shift, slotIndex")
    fun observeDateForTenant(tenantId: String, dateEpochDay: Long): Flow<List<VisitEntity>>
    fun observeDate(dateEpochDay: Long): Flow<List<VisitEntity>> = observeDateForTenant(DEFAULT_TENANT_ID, dateEpochDay)

    @Query("SELECT * FROM visits WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL AND companyId = :companyId ORDER BY cycleStartEpochDay, dateEpochDay, shift, slotIndex")
    fun observeItineraryForTenant(tenantId: String, companyId: String): Flow<List<VisitEntity>>
    fun observeItinerary(companyId: String): Flow<List<VisitEntity>> = observeItineraryForTenant(DEFAULT_TENANT_ID, companyId)

    @Query("SELECT * FROM visits WHERE tenantId = :tenantId AND (dirty = 1 OR syncStatus != 'SYNCED')")
    suspend fun dirtyForTenant(tenantId: String): List<VisitEntity>
    suspend fun dirty(): List<VisitEntity> = dirtyForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT * FROM visits WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL AND cycleStartEpochDay = :cycleStartEpochDay AND companyId IN (SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL)")
    suspend fun listCycleForTenant(tenantId: String, cycleStartEpochDay: Long): List<VisitEntity>
    suspend fun listCycle(cycleStartEpochDay: Long): List<VisitEntity> = listCycleForTenant(DEFAULT_TENANT_ID, cycleStartEpochDay)

    @Query("SELECT MAX(cycleStartEpochDay) FROM visits WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL AND cycleStartEpochDay < :cycleStartEpochDay AND companyId IN (SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL)")
    suspend fun latestCycleBeforeForTenant(tenantId: String, cycleStartEpochDay: Long): Long?
    suspend fun latestCycleBefore(cycleStartEpochDay: Long): Long? = latestCycleBeforeForTenant(DEFAULT_TENANT_ID, cycleStartEpochDay)

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM visits WHERE tenantId = :tenantId")
    suspend fun maxUpdatedAt(tenantId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VisitEntity>)

    @Query("UPDATE visits SET status = :status, updatedAt = :updatedAt, dirty = 1, syncStatus = 'PENDING' WHERE id = :visitId")
    suspend fun updateStatus(visitId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE visits SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1, syncStatus = 'PENDING', isDeleted = 1 WHERE companyId = :companyId AND isDeleted = 0")
    suspend fun softDeleteForCompany(companyId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE visits SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1, syncStatus = 'PENDING', isDeleted = 1 WHERE tenantId = :tenantId AND isDeleted = 0")
    suspend fun softDeleteAllForTenant(tenantId: String, deletedAt: Long = System.currentTimeMillis())
    suspend fun softDeleteAll(deletedAt: Long = System.currentTimeMillis()) = softDeleteAllForTenant(DEFAULT_TENANT_ID, deletedAt)

    @Query("UPDATE visits SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1, syncStatus = 'PENDING', isDeleted = 1 WHERE id IN (:visitIds)")
    suspend fun softDeleteByIds(visitIds: List<String>, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE visits SET dirty = 0, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markClean(ids: List<String>)

    @Query("UPDATE visits SET syncStatus = 'FAILED' WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<String>)

    @Query("DELETE FROM visits WHERE tenantId = :tenantId AND dirty = 0 AND (isDeleted = 1 OR deletedAt IS NOT NULL OR companyId NOT IN (SELECT id FROM companies WHERE tenantId = :tenantId AND isDeleted = 0 AND deletedAt IS NULL))")
    suspend fun purgeDeletedAndOrphansForTenant(tenantId: String)
}

@Dao
interface PrintLogDao {
    @Query("SELECT repId, visitId, COUNT(*) AS count FROM print_logs WHERE tenantId = :tenantId AND isDeleted = 0 GROUP BY repId, visitId")
    fun observeCountsForTenant(tenantId: String): Flow<List<PrintCountTuple>>
    fun observeCounts(): Flow<List<PrintCountTuple>> = observeCountsForTenant(DEFAULT_TENANT_ID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PrintLogEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE tenantId = :tenantId AND active = 1 AND isDeleted = 0 ORDER BY username COLLATE NOCASE")
    fun observeActiveForTenant(tenantId: String): Flow<List<UserEntity>>
    fun observeActive(): Flow<List<UserEntity>> = observeActiveForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT * FROM users WHERE tenantId = :tenantId AND username = :username COLLATE NOCASE AND active = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun findByUsernameForTenant(tenantId: String, username: String): UserEntity?
    suspend fun findByUsername(username: String): UserEntity? = findByUsernameForTenant(DEFAULT_TENANT_ID, username)

    @Query("SELECT * FROM users WHERE id = :userId AND active = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE tenantId = :tenantId ORDER BY username COLLATE NOCASE")
    suspend fun listAllForTenant(tenantId: String): List<UserEntity>
    suspend fun listAll(): List<UserEntity> = listAllForTenant(DEFAULT_TENANT_ID)

    @Query("SELECT COUNT(*) FROM users WHERE tenantId = :tenantId")
    suspend fun countForTenant(tenantId: String): Int
    suspend fun count(): Int = countForTenant(DEFAULT_TENANT_ID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserEntity>)

    @Query("UPDATE users SET passcodeHash = :passcodeHash, mustChangePasscode = 0, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :userId")
    suspend fun changePasscode(userId: String, passcodeHash: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE users SET active = 0, isDeleted = 1, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :userId")
    suspend fun deactivate(userId: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface AppSettingsDao {
    @Query("SELECT value FROM app_settings WHERE tenantId = :tenantId AND `key` = :key AND isDeleted = 0 LIMIT 1")
    suspend fun getValueForTenant(tenantId: String, key: String): String?
    suspend fun getValue(key: String): String? = getValueForTenant(DEFAULT_TENANT_ID, key)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: AppSettingEntity)
}
