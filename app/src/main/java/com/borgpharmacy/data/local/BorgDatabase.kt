package com.borgpharmacy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CompanyEntity::class,
        RepresentativeEntity::class,
        VisitEntity::class,
        PrintLogEntity::class,
        UserEntity::class,
        AppSettingEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class BorgDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun representativeDao(): RepresentativeDao
    abstract fun visitDao(): VisitDao
    abstract fun printLogDao(): PrintLogDao
    abstract fun userDao(): UserDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        const val DATABASE_NAME = "borg_pharmacy.db"
    }
}
