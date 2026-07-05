package com.borg.pharmacy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borg.pharmacy.data.local.entity.CompanyEntity
import com.borg.pharmacy.data.local.entity.ScheduledVisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PharmacyDao {
    @Query("SELECT * FROM companies")
    fun getAllCompaniesFlow(): Flow<List<CompanyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: CompanyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanies(companies: List<CompanyEntity>)

    @Query("SELECT * FROM companies WHERE isSynced = 0")
    suspend fun getUnsyncedCompanies(): List<CompanyEntity>

    @Query("UPDATE companies SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markCompaniesAsSynced(ids: List<String>)

    @Query("SELECT * FROM scheduled_visits WHERE dayOfCycle = :day")
    fun getVisitsForDay(day: Int): Flow<List<ScheduledVisitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisits(visits: List<ScheduledVisitEntity>)
}
