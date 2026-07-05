package com.borg.pharmacy.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.borg.pharmacy.data.local.dao.PharmacyDao
import com.borg.pharmacy.data.local.entity.CompanyEntity
import com.borg.pharmacy.data.local.entity.RepresentativeEntity
import com.borg.pharmacy.data.local.entity.ScheduledVisitEntity

@Database(
    entities = [CompanyEntity::class, ScheduledVisitEntity::class, RepresentativeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PharmacyDatabase : RoomDatabase() {
    abstract fun pharmacyDao(): PharmacyDao

    companion object {
        @Volatile
        private var INSTANCE: PharmacyDatabase? = null

        fun getDatabase(context: Context): PharmacyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PharmacyDatabase::class.java,
                    "borg_pharmacy_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
