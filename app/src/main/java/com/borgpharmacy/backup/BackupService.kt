package com.borgpharmacy.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.borgpharmacy.data.local.BorgDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupService(
    private val context: Context,
    private val database: BorgDatabase,
) {
    private val timestampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    @Suppress("DEPRECATION")
    fun backupDirectory(): File {
        val root = Environment.getExternalStorageDirectory()
        return File(root, "BORG PHARMACY/BACKUP").apply { mkdirs() }
    }

    suspend fun ensureDirectories(): File = withContext(Dispatchers.IO) { backupDirectory() }

    suspend fun dumpDatabase(reason: String = "manual"): File = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(BorgDatabase.DATABASE_NAME)
        val dir = backupDirectory()
        val stamp = timestampFormatter.format(Date())
        val outFile = File(dir, "borg_pharmacy_${reason}_$stamp.db")
        if (dbFile.exists()) {
            dbFile.copyTo(outFile, overwrite = true)
            copySidecar("-wal", outFile)
            copySidecar("-shm", outFile)
        } else {
            outFile.writeText("Database file is not created yet.")
        }
        outFile
    }

    private fun copySidecar(suffix: String, targetDbFile: File) {
        val source = File(context.getDatabasePath(BorgDatabase.DATABASE_NAME).absolutePath + suffix)
        if (source.exists()) {
            source.copyTo(File(targetDbFile.absolutePath + suffix), overwrite = true)
        }
    }

    suspend fun restoreDatabaseFrom(uri: Uri): Unit = withContext(Dispatchers.IO) {
        database.close()
        val dbFile = context.getDatabasePath(BorgDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected backup file")
    }

    suspend fun latestBackup(): File? = withContext(Dispatchers.IO) {
        backupDirectory().listFiles { file -> file.name.endsWith(".db") }
            ?.maxByOrNull { it.lastModified() }
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
