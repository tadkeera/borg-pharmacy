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

        // Room validates indices after migration. Create the same indices declared on @Entity classes.
        db.execSQL("CREATE INDEX IF NOT EXISTS index_companies_tenantId ON companies(tenantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_companies_tenantId_name ON companies(tenantId, name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_representatives_tenantId ON representatives(tenantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_representatives_companyId ON representatives(companyId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_representatives_phone ON representatives(phone)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_representatives_tenantId_phone ON representatives(tenantId, phone)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_visits_tenantId ON visits(tenantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_visits_companyId ON visits(companyId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_visits_cycleStartEpochDay ON visits(cycleStartEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_visits_dateEpochDay ON visits(dateEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_visits_shift ON visits(shift)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_visits_tenantId_updatedAt ON visits(tenantId, updatedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_print_logs_tenantId ON print_logs(tenantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_print_logs_repId ON print_logs(repId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_print_logs_visitId ON print_logs(visitId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_users_tenantId ON users(tenantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_settings_tenantId ON app_settings(tenantId)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v1.0.38/v1.0.39 استخدم tenant افتراضياً مؤقتاً 000...000.
        // بعد تفعيل Auth أصبح Tenant صيدلية برج الأطباء هو 000...001، لذلك ننقل البيانات المحلية إليه حتى لا تختفي الشركات والجداول بعد الدخول.
        listOf("companies", "representatives", "visits", "print_logs", "users", "app_settings").forEach { table ->
            db.execSQL("UPDATE $table SET tenantId = '$DEFAULT_TENANT_ID' WHERE tenantId IS NULL OR tenantId = '00000000-0000-0000-0000-000000000000'")
        }
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
