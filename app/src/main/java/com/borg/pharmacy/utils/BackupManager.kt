package com.borg.pharmacy.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    fun createBackup(context: Context, databaseData: String) {
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val root = File(Environment.getExternalStorageDirectory(), "BORG PHARMACY/BACKUP")
            if (!root.exists()) {
                root.mkdirs()
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(root, "backup_$timeStamp.json")
            
            try {
                FileOutputStream(backupFile).use { output ->
                    output.write(databaseData.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
