package com.borgpharmacy

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.borgpharmacy.backup.BackupService
import com.borgpharmacy.communications.WhatsAppMessenger
import com.borgpharmacy.data.local.BorgDatabase
import com.borgpharmacy.data.remote.SupabaseSyncService
import com.borgpharmacy.data.repository.BorgRepository
import com.borgpharmacy.data.repository.OfflineFirstBorgRepository
import com.borgpharmacy.domain.CycleCalculator
import com.borgpharmacy.domain.ScheduleGenerator

class BorgPharmacyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE companies ADD COLUMN baseDayIndex INTEGER")
        db.execSQL("ALTER TABLE companies ADD COLUMN baseShift TEXT")
    }
}

class AppContainer(private val application: Application) {
    val database: BorgDatabase by lazy {
        Room.databaseBuilder(application, BorgDatabase::class.java, BorgDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    val backupService: BackupService by lazy { BackupService(application, database) }
    val syncService: SupabaseSyncService by lazy { SupabaseSyncService() }
    val cycleCalculator: CycleCalculator by lazy { CycleCalculator() }
    val scheduleGenerator: ScheduleGenerator by lazy { ScheduleGenerator(cycleCalculator) }
    val repository: BorgRepository by lazy {
        OfflineFirstBorgRepository(
            db = database,
            backupService = backupService,
            syncService = syncService,
            scheduleGenerator = scheduleGenerator,
            cycleCalculator = cycleCalculator,
        )
    }
    val whatsAppMessenger: WhatsAppMessenger by lazy { WhatsAppMessenger(application) }
}
