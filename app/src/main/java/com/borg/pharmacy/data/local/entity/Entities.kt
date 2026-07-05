package com.borg.pharmacy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val isSynced: Boolean = false // Track sync state
)

@Entity(tableName = "scheduled_visits")
data class ScheduledVisitEntity(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val companyId: String,
    val dayOfCycle: Int,
    val shift: String,
    val isSynced: Boolean = false // Track sync state
)

@Entity(tableName = "representatives")
data class RepresentativeEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val phone: String,
    val printCount: Int = 0,
    val isSynced: Boolean = false // Track sync state
)
