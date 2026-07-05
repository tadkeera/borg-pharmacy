package com.borg.pharmacy.utils

import android.content.Context
import android.util.Log

object PrinterManager {
    
    /**
     * Connects to a paired 80mm Bluetooth printer and prints the "Visit Permission Card".
     */
    fun printVisitCard(
        context: Context,
        repName: String,
        companyName: String,
        date: String,
        shift: String,
        printCount: Int
    ) {
        val receiptData = buildString {
            append("--------------------------------\n")
            append("      مستشفى برج الأطباء        \n")
            append("        إدارة الصيدلية          \n")
            append("--------------------------------\n")
            append("مندوب: $repName\n")
            append("الشركة: $companyName\n")
            append("التاريخ: $date\n")
            append("الفترة: $shift\n")
            append("--------------------------------\n")
            append("عدد مرات الطباعة: $printCount\n")
            append("--------------------------------\n\n\n")
        }

        // Logic to send `receiptData` bytes over Bluetooth output stream to 80mm printer
        Log.d("PrinterManager", "Printing Receipt:\n$receiptData")
    }
}
