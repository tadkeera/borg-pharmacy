package com.borgpharmacy

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.borgpharmacy.backup.BackupService
import com.borgpharmacy.communications.WhatsAppMessenger
import com.borgpharmacy.data.local.BorgDatabase
import com.borgpharmacy.data.local.DEFAULT_TENANT_ID
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
        addColumnIfMissing(db, "companies", "baseDayIndex", "ALTER TABLE companies ADD COLUMN baseDayIndex INTEGER")
        addColumnIfMissing(db, "companies", "baseShift", "ALTER TABLE companies ADD COLUMN baseShift TEXT")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("companies", "representatives", "visits", "print_logs", "users", "app_settings").forEach { table ->
            addColumnIfMissing(db, table, "tenantId", "ALTER TABLE $table ADD COLUMN tenantId TEXT NOT NULL DEFAULT '$DEFAULT_TENANT_ID'")
            addColumnIfMissing(db, table, "syncStatus", "ALTER TABLE $table ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
            addColumnIfMissing(db, table, "isDeleted", "ALTER TABLE $table ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        }
        addColumnIfMissing(db, "print_logs", "updatedAt", "ALTER TABLE print_logs ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "app_settings", "updatedAt", "ALTER TABLE app_settings ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

        db.execSQL("UPDATE companies SET isDeleted = 1 WHERE deletedAt IS NOT NULL")
        db.execSQL("UPDATE representatives SET isDeleted = 1 WHERE deletedAt IS NOT NULL")
        db.execSQL("UPDATE visits SET isDeleted = 1 WHERE deletedAt IS NOT NULL")
        db.execSQL("UPDATE users SET isDeleted = CASE WHEN active = 1 THEN 0 ELSE 1 END")
    }
}

private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, sql: String) {
    db.query("PRAGMA table_info($table)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return
        }
    }
    db.execSQL(sql)
}

class AppContainer(private val application: Application) {
    val database: BorgDatabase by lazy {
        Room.databaseBuilder(application, BorgDatabase::class.java, BorgDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
