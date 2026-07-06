package com.borgpharmacy.communications

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.borgArabicName
import java.time.format.DateTimeFormatter

class WhatsAppMessenger(private val context: Context) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
            "لا توجد زيارات مجدولة حاليًا."
        } else {
            visits.sortedWith(compareBy<Visit> { it.date }.thenBy { it.shift.ordinal }).joinToString("\n") { visit ->
                "- الأسبوع ${visit.weekOfCycle}: ${visit.date.format(formatter)} (${visit.date.dayOfWeek.borgArabicName()})، ${visit.shift.arabicName}"
            }
        }
        return """
            صيدلية برج الأطباء - إدارة الصيدلية
            الأخ/الأخت ${representative.name}
            شركة: ${company.name}
            التصنيف: ${company.tier.label}

            جدول الزيارات للدورة الحالية:
            $itinerary

            يرجى الالتزام بالموعد المحدد وإحضار التعريف المهني.
        """.trimIndent()
    }
}
