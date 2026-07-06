package com.borgpharmacy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    suspend fun listActive(): List<CompanyEntity>

    @Query("SELECT * FROM companies WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE LIMIT 50")
    suspend fun search(query: String): List<CompanyEntity>

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CompanyEntity?

    @Query("SELECT * FROM companies WHERE dirty = 1")
    suspend fun dirty(): List<CompanyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompanyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CompanyEntity>)

    @Query("UPDATE companies SET tier = :tier, updatedAt = :updatedAt, dirty = 1 WHERE id = :companyId")
    suspend fun updateTier(companyId: String, tier: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET name = :name, updatedAt = :updatedAt, dirty = 1 WHERE id = :companyId")
    suspend fun updateName(companyId: String, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1 WHERE id = :companyId")
    suspend fun softDelete(companyId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1 WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET dirty = 0 WHERE id IN (:ids)")
    suspend fun markClean(ids: List<String>)

    @Query("SELECT tier, COUNT(*) AS count FROM companies WHERE deletedAt IS NULL GROUP BY tier")
    fun observeTierCounts(): Flow<List<TierCountTuple>>
}

@Dao
interface RepresentativeDao {
    @Query("SELECT * FROM representatives WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<RepresentativeEntity>>

    @Query("SELECT * FROM representatives WHERE companyId = :companyId AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeForCompany(companyId: String): Flow<List<RepresentativeEntity>>

    @Query("SELECT * FROM representatives WHERE dirty = 1")
    suspend fun dirty(): List<RepresentativeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RepresentativeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RepresentativeEntity>)

    @Query("UPDATE representatives SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1 WHERE id = :repId")
    suspend fun softDelete(repId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE representatives SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1 WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE representatives SET dirty = 0 WHERE id IN (:ids)")
    suspend fun markClean(ids: List<String>)
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE deletedAt IS NULL ORDER BY dateEpochDay, shift, slotIndex")
    fun observeActive(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE deletedAt IS NULL AND cycleStartEpochDay = :cycleStartEpochDay ORDER BY dateEpochDay, shift, slotIndex")
    fun observeCycle(cycleStartEpochDay: Long): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE deletedAt IS NULL AND dateEpochDay = :dateEpochDay ORDER BY shift, slotIndex")
    fun observeDate(dateEpochDay: Long): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE deletedAt IS NULL AND companyId = :companyId ORDER BY cycleStartEpochDay, dateEpochDay, shift, slotIndex")
    fun observeItinerary(companyId: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE dirty = 1")
    suspend fun dirty(): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE deletedAt IS NULL AND cycleStartEpochDay = :cycleStartEpochDay")
    suspend fun listCycle(cycleStartEpochDay: Long): List<VisitEntity>

    @Query("SELECT MAX(cycleStartEpochDay) FROM visits WHERE deletedAt IS NULL AND cycleStartEpochDay < :cycleStartEpochDay")
    suspend fun latestCycleBefore(cycleStartEpochDay: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VisitEntity>)

    @Query("UPDATE visits SET status = :status, updatedAt = :updatedAt, dirty = 1 WHERE id = :visitId")
    suspend fun updateStatus(visitId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE visits SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1 WHERE companyId = :companyId AND deletedAt IS NULL")
    suspend fun softDeleteForCompany(companyId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE visits SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1 WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE visits SET deletedAt = :deletedAt, updatedAt = :deletedAt, dirty = 1 WHERE id IN (:visitIds)")
    suspend fun softDeleteByIds(visitIds: List<String>, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE visits SET dirty = 0 WHERE id IN (:ids)")
    suspend fun markClean(ids: List<String>)
}

@Dao
interface PrintLogDao {
    @Query("SELECT repId, visitId, COUNT(*) AS count FROM print_logs GROUP BY repId, visitId")
    fun observeCounts(): Flow<List<PrintCountTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PrintLogEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE active = 1 ORDER BY username COLLATE NOCASE")
    fun observeActive(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE AND active = 1 LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId AND active = 1 LIMIT 1")
    suspend fun getById(userId: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserEntity)

    @Query("UPDATE users SET passcodeHash = :passcodeHash, mustChangePasscode = 0, updatedAt = :updatedAt WHERE id = :userId")
    suspend fun changePasscode(userId: String, passcodeHash: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE users SET active = 0, updatedAt = :updatedAt WHERE id = :userId")
    suspend fun deactivate(userId: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface AppSettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: AppSettingEntity)
}
