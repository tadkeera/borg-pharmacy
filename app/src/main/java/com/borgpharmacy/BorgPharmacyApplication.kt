package com.borgpharmacy

import android.app.Application
import androidx.room.Room
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

class AppContainer(private val application: Application) {
    val database: BorgDatabase by lazy {
        Room.databaseBuilder(application, BorgDatabase::class.java, BorgDatabase.DATABASE_NAME)
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
