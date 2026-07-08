package com.borgpharmacy.communications

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.borgArabicName

class WhatsAppMessenger(private val context: Context) {
    fun openItinerary(company: Company, representative: Representative, visits: List<Visit>) {
        val normalizedPhone = representative.phone
            .replace("+", "")
            .replace(" ", "")
            .ifBlank { "967" }
        val text = buildMessage(company, representative, visits)
        val uri = Uri.parse("https://wa.me/$normalizedPhone?text=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun buildMessage(company: Company, representative: Representative, visits: List<Visit>): String {
        val itinerary = if (visits.isEmpty()) {
            "•   *لا توجد زيارات مجدولة حاليًا.*"
        } else {
            visits.sortedWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.date }.thenBy { it.shift.ordinal })
                .joinToString("\n") { visit ->
                    "•   *الأسبوع ${visit.weekOfCycle}:  (${visit.date.dayOfWeek.borgArabicName()}) - ${visit.shift.arabicName}*"
                }
        }

        return """
            *صيدلية برج الأطباء - إدارة الصيدلية*

            *د/  ${representative.name}*

            *شركة: ${company.name}*

            *جدول الزيارات*

            $itinerary

            *يرجى الالتزام بالموعد المحدد.*
        """.trimIndent()
    }
}
