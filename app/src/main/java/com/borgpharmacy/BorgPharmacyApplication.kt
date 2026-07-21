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

private const val LEGACY_TENANT_ID = "00000000-0000-0000-0000-000000000000"

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
        addTenantSyncColumns(db)
        createRoomIndices(db)
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        moveLegacyTenantRowsToActiveTenant(db)
    }
}

private val MIGRATION_1_5 = object : Migration(1, 5) { override fun migrate(db: SupportSQLiteDatabase) = rebuildTenantSchema(db) }
private val MIGRATION_2_5 = object : Migration(2, 5) { override fun migrate(db: SupportSQLiteDatabase) = rebuildTenantSchema(db) }
private val MIGRATION_3_5 = object : Migration(3, 5) { override fun migrate(db: SupportSQLiteDatabase) = rebuildTenantSchema(db) }
private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) = rebuildTenantSchema(db) }

private fun addTenantSyncColumns(db: SupportSQLiteDatabase) {
    listOf("companies", "representatives", "visits", "print_logs", "users", "app_settings").forEach { table ->
        addColumnIfMissing(db, table, "tenantId", "ALTER TABLE $table ADD COLUMN tenantId TEXT NOT NULL DEFAULT '$LEGACY_TENANT_ID'")
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

private fun moveLegacyTenantRowsToActiveTenant(db: SupportSQLiteDatabase) {
    listOf("companies", "representatives", "visits", "print_logs", "users", "app_settings").forEach { table ->
        if (tableExists(db, table) && columnExists(db, table, "tenantId")) {
            db.execSQL("UPDATE $table SET tenantId = '$DEFAULT_TENANT_ID' WHERE tenantId IS NULL OR tenantId = '$LEGACY_TENANT_ID'")
        }
    }
}

private fun rebuildTenantSchema(db: SupportSQLiteDatabase) {
    listOf("companies", "representatives", "visits", "print_logs", "users", "app_settings").forEach { table ->
        db.execSQL("DROP TABLE IF EXISTS ${table}_old")
        if (tableExists(db, table)) db.execSQL("ALTER TABLE $table RENAME TO ${table}_old")
    }

    createExactRoomTables(db)
    copyOldRows(db)
    listOf("print_logs", "visits", "representatives", "companies", "users", "app_settings").forEach { table ->
        db.execSQL("DROP TABLE IF EXISTS ${table}_old")
    }
    createRoomIndices(db)
}

private fun createExactRoomTables(db: SupportSQLiteDatabase) {
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS companies (
            id TEXT NOT NULL PRIMARY KEY,
            tenantId TEXT NOT NULL DEFAULT '$LEGACY_TENANT_ID',
            name TEXT NOT NULL,
            tier TEXT NOT NULL,
            baseDayIndex INTEGER,
            baseShift TEXT,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER,
            dirty INTEGER NOT NULL,
            syncStatus TEXT NOT NULL DEFAULT 'SYNCED',
            isDeleted INTEGER NOT NULL DEFAULT 0
        )
    """.trimIndent())
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS representatives (
            id TEXT NOT NULL PRIMARY KEY,
            tenantId TEXT NOT NULL DEFAULT '$LEGACY_TENANT_ID',
            companyId TEXT NOT NULL,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER,
            dirty INTEGER NOT NULL,
            syncStatus TEXT NOT NULL DEFAULT 'SYNCED',
            isDeleted INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(companyId) REFERENCES companies(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
    """.trimIndent())
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS visits (
            id TEXT NOT NULL PRIMARY KEY,
            tenantId TEXT NOT NULL DEFAULT '$LEGACY_TENANT_ID',
            companyId TEXT NOT NULL,
            cycleStartEpochDay INTEGER NOT NULL,
            dayOfCycle INTEGER NOT NULL,
            weekOfCycle INTEGER NOT NULL,
            dateEpochDay INTEGER NOT NULL,
            shift TEXT NOT NULL,
            slotIndex INTEGER NOT NULL,
            status TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER,
            dirty INTEGER NOT NULL,
            syncStatus TEXT NOT NULL DEFAULT 'SYNCED',
            isDeleted INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(companyId) REFERENCES companies(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
    """.trimIndent())
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS print_logs (
            id TEXT NOT NULL PRIMARY KEY,
            tenantId TEXT NOT NULL DEFAULT '$LEGACY_TENANT_ID',
            repId TEXT NOT NULL,
            visitId TEXT NOT NULL,
            printedAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL DEFAULT 0,
            syncStatus TEXT NOT NULL DEFAULT 'SYNCED',
            isDeleted INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(repId) REFERENCES representatives(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(visitId) REFERENCES visits(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
    """.trimIndent())
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT NOT NULL PRIMARY KEY,
            tenantId TEXT NOT NULL DEFAULT '$LEGACY_TENANT_ID',
            username TEXT NOT NULL,
            displayName TEXT NOT NULL,
            role TEXT NOT NULL,
            passcodeHash TEXT NOT NULL,
            mustChangePasscode INTEGER NOT NULL,
            active INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            syncStatus TEXT NOT NULL DEFAULT 'SYNCED',
            isDeleted INTEGER NOT NULL DEFAULT 0
        )
    """.trimIndent())
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS app_settings (
            `key` TEXT NOT NULL PRIMARY KEY,
            value TEXT NOT NULL,
            tenantId TEXT NOT NULL DEFAULT '$LEGACY_TENANT_ID',
            updatedAt INTEGER NOT NULL DEFAULT 0,
            syncStatus TEXT NOT NULL DEFAULT 'SYNCED',
            isDeleted INTEGER NOT NULL DEFAULT 0
        )
    """.trimIndent())
}

private fun copyOldRows(db: SupportSQLiteDatabase) {
    if (tableExists(db, "companies_old")) {
        db.execSQL("""
            INSERT OR REPLACE INTO companies (id, tenantId, name, tier, baseDayIndex, baseShift, createdAt, updatedAt, deletedAt, dirty, syncStatus, isDeleted)
            SELECT id, ${tenantExpr(db, "companies_old")}, name, tier, baseDayIndex, baseShift, createdAt, updatedAt, deletedAt,
                   ${intExpr(db, "companies_old", "dirty", "0")}, ${textExpr(db, "companies_old", "syncStatus", "'SYNCED'")}, ${isDeletedExpr(db, "companies_old")}
            FROM companies_old
        """.trimIndent())
    }
    if (tableExists(db, "representatives_old")) {
        db.execSQL("""
            INSERT OR REPLACE INTO representatives (id, tenantId, companyId, name, phone, createdAt, updatedAt, deletedAt, dirty, syncStatus, isDeleted)
            SELECT id, ${tenantExpr(db, "representatives_old")}, companyId, name, phone, createdAt, updatedAt, deletedAt,
                   ${intExpr(db, "representatives_old", "dirty", "0")}, ${textExpr(db, "representatives_old", "syncStatus", "'SYNCED'")}, ${isDeletedExpr(db, "representatives_old")}
            FROM representatives_old
            WHERE companyId IN (SELECT id FROM companies)
        """.trimIndent())
    }
    if (tableExists(db, "visits_old")) {
        db.execSQL("""
            INSERT OR REPLACE INTO visits (id, tenantId, companyId, cycleStartEpochDay, dayOfCycle, weekOfCycle, dateEpochDay, shift, slotIndex, status, createdAt, updatedAt, deletedAt, dirty, syncStatus, isDeleted)
            SELECT id, ${tenantExpr(db, "visits_old")}, companyId, cycleStartEpochDay, dayOfCycle, weekOfCycle, dateEpochDay, shift, slotIndex, status, createdAt, updatedAt, deletedAt,
                   ${intExpr(db, "visits_old", "dirty", "0")}, ${textExpr(db, "visits_old", "syncStatus", "'SYNCED'")}, ${isDeletedExpr(db, "visits_old")}
            FROM visits_old
            WHERE companyId IN (SELECT id FROM companies)
        """.trimIndent())
    }
    if (tableExists(db, "print_logs_old")) {
        db.execSQL("""
            INSERT OR REPLACE INTO print_logs (id, tenantId, repId, visitId, printedAt, updatedAt, syncStatus, isDeleted)
            SELECT id, ${tenantExpr(db, "print_logs_old")}, repId, visitId, printedAt, ${intExpr(db, "print_logs_old", "updatedAt", "printedAt")},
                   ${textExpr(db, "print_logs_old", "syncStatus", "'SYNCED'")}, ${intExpr(db, "print_logs_old", "isDeleted", "0")}
            FROM print_logs_old
            WHERE repId IN (SELECT id FROM representatives) AND visitId IN (SELECT id FROM visits)
        """.trimIndent())
    }
    if (tableExists(db, "users_old")) {
        db.execSQL("""
            INSERT OR REPLACE INTO users (id, tenantId, username, displayName, role, passcodeHash, mustChangePasscode, active, createdAt, updatedAt, syncStatus, isDeleted)
            SELECT id, ${tenantExpr(db, "users_old")}, username, displayName, role, passcodeHash, mustChangePasscode, active, createdAt, updatedAt,
                   ${textExpr(db, "users_old", "syncStatus", "'SYNCED'")}, ${intExpr(db, "users_old", "isDeleted", "CASE WHEN active = 1 THEN 0 ELSE 1 END")}
            FROM users_old
        """.trimIndent())
    }
    if (tableExists(db, "app_settings_old")) {
        db.execSQL("""
            INSERT OR REPLACE INTO app_settings (`key`, value, tenantId, updatedAt, syncStatus, isDeleted)
            SELECT `key`, value, ${tenantExpr(db, "app_settings_old")}, ${intExpr(db, "app_settings_old", "updatedAt", "0")},
                   ${textExpr(db, "app_settings_old", "syncStatus", "'SYNCED'")}, ${intExpr(db, "app_settings_old", "isDeleted", "0")}
            FROM app_settings_old
        """.trimIndent())
    }
}

private fun tenantExpr(db: SupportSQLiteDatabase, table: String): String =
    if (columnExists(db, table, "tenantId")) {
        "CASE WHEN tenantId IS NULL OR tenantId = '$LEGACY_TENANT_ID' THEN '$DEFAULT_TENANT_ID' ELSE tenantId END"
    } else {
        "'$DEFAULT_TENANT_ID'"
    }

private fun textExpr(db: SupportSQLiteDatabase, table: String, column: String, fallback: String): String =
    if (columnExists(db, table, column)) "COALESCE($column, $fallback)" else fallback

private fun intExpr(db: SupportSQLiteDatabase, table: String, column: String, fallback: String): String =
    if (columnExists(db, table, column)) "COALESCE($column, $fallback)" else fallback

private fun isDeletedExpr(db: SupportSQLiteDatabase, table: String): String = when {
    columnExists(db, table, "isDeleted") -> "COALESCE(isDeleted, CASE WHEN deletedAt IS NOT NULL THEN 1 ELSE 0 END)"
    columnExists(db, table, "deletedAt") -> "CASE WHEN deletedAt IS NOT NULL THEN 1 ELSE 0 END"
    else -> "0"
}

private fun createRoomIndices(db: SupportSQLiteDatabase) {
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
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users(username)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_users_tenantId ON users(tenantId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_app_settings_tenantId ON app_settings(tenantId)")
}

private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, sql: String) {
    if (!columnExists(db, table, column)) db.execSQL(sql)
}

private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
    db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
        return cursor.moveToFirst()
    }
}

private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    if (!tableExists(db, table)) return false
    db.query("PRAGMA table_info($table)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return true
        }
    }
    return false
}

class AppContainer(private val application: Application) {
    val database: BorgDatabase by lazy {
        Room.databaseBuilder(application, BorgDatabase::class.java, BorgDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5, MIGRATION_4_5)
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
