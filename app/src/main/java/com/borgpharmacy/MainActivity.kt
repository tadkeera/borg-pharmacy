package com.borgpharmacy

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.content.res.ResourcesCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.Shift
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.borgArabicName
import com.borgpharmacy.print.PassPrintManager
import com.borgpharmacy.ui.BorgAppViewModel
import com.borgpharmacy.ui.BorgUiState
import com.borgpharmacy.ui.BorgViewModelFactory
import com.borgpharmacy.ui.screens.BorgApp
import com.borgpharmacy.ui.theme.BorgPharmacyTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: BorgAppViewModel by viewModels {
        BorgViewModelFactory((application as BorgPharmacyApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBackupStorageAccess()
        setContent { RootContent() }
    }

    @Composable
    private fun RootContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val container = (application as BorgPharmacyApplication).container
        val printer = PassPrintManager(this)

        val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            val csv = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            viewModel.importCompaniesCsv(csv)
        }
        val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                container.backupService.restoreDatabaseFrom(uri)
                restartApplication()
            }
        }

        BorgPharmacyTheme {
            BorgApp(
                state = state,
                onLogin = viewModel::login,
                onChangeForcedPasscode = viewModel::changeForcedPasscode,
                onLogout = viewModel::logout,
                onAddCompany = viewModel::addCompany,
                onImportCsv = { csvLauncher.launch("text/*") },
                onExportCompanies = { format -> exportCompanies(format, state.companies) },
                onExportSchedules = { format -> exportSchedules(format, state) },
                onUpdateCompanyName = viewModel::updateCompanyName,
                onDeleteCompany = viewModel::deleteCompany,
                onDeleteAllCompanies = viewModel::deleteAllCompanies,
                onAddRepresentative = viewModel::addRepresentative,
                onDeleteRepresentative = viewModel::deleteRepresentative,
                onCreateUser = viewModel::createUser,
                onSaveBotSettings = viewModel::saveBotSettings,
                onRefreshBotData = viewModel::refreshBotSettings,
                onMarkVisitStatus = viewModel::markVisitStatus,
                onPrint = { company: Company, rep: Representative, visit: Visit ->
                    printer.printPass(company, rep, visit)
                    viewModel.recordPrint(rep.id, visit.id)
                },
                onWhatsApp = { company: Company, rep: Representative ->
                    container.whatsAppMessenger.openItinerary(
                        company = company,
                        representative = rep,
                        visits = state.visitsByCompany[company.id].orEmpty()
                            .filter { it.cycleStartEpochDay == state.cycleInfo.currentCycleStart.toEpochDay() },
                    )
                },
                onBackup = viewModel::backupNow,
                onRestore = { restoreLauncher.launch("*/*") },
                onDriveBackup = ::shareLatestBackupToDrive,
                onSync = viewModel::syncNow,
                onDismissMessage = viewModel::clearSnackbar,
            )
        }
    }

    private fun exportCompanies(format: String, companies: List<Company>) {
        val dir = File(getExternalFilesDir(null), "EXPORTS").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = when (format.lowercase(Locale.US)) {
            "csv" -> File(dir, "borg_companies_$stamp.csv").also { writeCompaniesCsv(it, companies) }
            "html" -> File(dir, "borg_companies_$stamp.html").also { writeCompaniesHtml(it, companies) }
            "pdf" -> File(dir, "borg_companies_$stamp.pdf").also { writeCompaniesPdf(it, companies) }
            else -> return
        }
        val uri = (application as BorgPharmacyApplication).container.backupService.uriFor(file)
        val mime = when (file.extension.lowercase(Locale.US)) {
            "csv" -> "text/csv"
            "html" -> "text/html"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "تصدير شركات صيدلية برج الأطباء")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "تصدير الشركات"))
    }

    private fun writeCompaniesCsv(file: File, companies: List<Company>) {
        fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
        file.writeText(
            buildString {
                append("\uFEFFCompany ID,Company Name,Tier\n")
                companies.sortedBy { it.name }.forEach { company ->
                    append(csv(company.id)).append(',')
                    append(csv(company.name)).append(',')
                    append(csv(company.tier.name)).append('\n')
                }
            },
            Charsets.UTF_8,
        )
    }

    private fun writeCompaniesHtml(file: File, companies: List<Company>) {
        fun esc(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        file.writeText(
            buildString {
                append("""
                    <!DOCTYPE html><html lang="ar" dir="rtl"><head><meta charset="UTF-8">
                    <style>body{font-family:Arial,sans-serif;padding:24px}table{width:100%;border-collapse:collapse}th{background:#0E4D8F;color:white}td,th{border:1px solid #ddd;padding:8px;text-align:right}tr:nth-child(even){background:#f7f9fc}</style>
                    <title>شركات صيدلية برج الأطباء</title></head><body>
                    <h1>شركات صيدلية برج الأطباء</h1><p>الإجمالي: ${companies.size}</p><table><thead><tr><th>#</th><th>اسم الشركة</th><th>Company ID</th><th>التقييم</th></tr></thead><tbody>
                """.trimIndent())
                companies.sortedBy { it.name }.forEachIndexed { index, company ->
                    append("<tr><td>${index + 1}</td><td>${esc(company.name)}</td><td>${esc(company.id)}</td><td>${esc(company.tier.name)}</td></tr>")
                }
                append("</tbody></table></body></html>")
            },
            Charsets.UTF_8,
        )
    }

    private fun writeCompaniesPdf(file: File, companies: List<Company>) {
        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(14, 77, 143)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 44f
        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 44f
        }
        canvas.drawText("شركات صيدلية برج الأطباء", pageWidth - 36f, y, titlePaint)
        y += 30f
        canvas.drawText("الإجمالي: ${companies.size}", pageWidth - 36f, y, paint)
        y += 26f
        companies.sortedBy { it.name }.forEachIndexed { index, company ->
            if (y > pageHeight - 40f) newPage()
            val line = "${index + 1}. ${company.name}    ${company.tier.name}    ${company.id.take(8)}"
            canvas.drawText(line, pageWidth - 36f, y, paint)
            y += 18f
        }
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun exportSchedules(format: String, state: BorgUiState) {
        val dir = File(getExternalFilesDir(null), "EXPORTS").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = when (format.lowercase(Locale.US)) {
            "csv" -> File(dir, "borg_weekly_schedules_$stamp.csv").also { writeSchedulesCsv(it, state) }
            "html" -> File(dir, "borg_weekly_schedules_$stamp.html").also { writeSchedulesHtml(it, state) }
            "pdf" -> File(dir, "borg_weekly_schedules_$stamp.pdf").also { writeSchedulesPdf(it, state) }
            else -> return
        }
        shareFile(file, "تصدير جداول زيارات صيدلية برج الأطباء")
    }

    private fun shareFile(file: File, subject: String) {
        val uri = (application as BorgPharmacyApplication).container.backupService.uriFor(file)
        val mime = when (file.extension.lowercase(Locale.US)) {
            "csv" -> "text/csv"
            "html" -> "text/html"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, subject))
    }

    private fun writeSchedulesCsv(file: File, state: BorgUiState) {
        fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
        val companies = state.companies.associateBy { it.id }
        val currentEpoch = state.cycleInfo.currentCycleStart.toEpochDay()
        file.writeText(buildString {
            append("\uFEFFWeek,Date,Day,Shift,No,Company,Company ID\n")
            (1..4).forEach { week ->
                val weekStart = state.cycleInfo.currentCycleStart.plusDays(((week - 1) * 7).toLong())
                (0..4).forEach { dayOffset ->
                    val date = weekStart.plusDays(dayOffset.toLong())
                    listOf(Shift.MORNING, Shift.EVENING).forEach { shift ->
                        state.visits.filter { it.cycleStartEpochDay == currentEpoch && it.date == date && it.shift == shift }
                            .scheduleDisplaySorted().forEachIndexed { index, visit ->
                                append(week).append(',').append(csv(date.toString())).append(',')
                                append(csv(visit.date.dayOfWeek.borgArabicName())).append(',')
                                append(csv(visit.shift.arabicName)).append(',').append(index + 1).append(',')
                                append(csv(companies[visit.companyId]?.name ?: "شركة غير معروفة")).append(',')
                                append(csv(visit.companyId)).append('\n')
                            }
                    }
                }
            }
        }, Charsets.UTF_8)
    }

    private fun writeSchedulesHtml(file: File, state: BorgUiState) {
        fun esc(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val companies = state.companies.associateBy { it.id }
        val currentEpoch = state.cycleInfo.currentCycleStart.toEpochDay()
        fun listHtml(date: java.time.LocalDate, shift: Shift): String {
            val visits = state.visits.filter { it.cycleStartEpochDay == currentEpoch && it.date == date && it.shift == shift }.scheduleDisplaySorted()
            return buildString {
                append("<div class='shift ${if (shift == Shift.MORNING) "morning" else "evening"}'><h4>${esc(shift.arabicName)} <span>${visits.size}</span></h4>")
                visits.forEachIndexed { index, visit -> append("<div class='company'><b>${index + 1}</b><span>${esc(companies[visit.companyId]?.name ?: "شركة غير معروفة")}</span></div>") }
                append("</div>")
            }
        }
        file.writeText(buildString {
            append("""
                <!DOCTYPE html><html lang="ar" dir="rtl"><head><meta charset="UTF-8"><style>
                @page{size:A4 landscape;margin:7mm}body{font-family:Arial,Tahoma,sans-serif;margin:0;background:#eef4fb;color:#082B52}.page{page-break-after:always;background:#fff;border-radius:18px;padding:10mm;min-height:190mm}.header{display:flex;justify-content:space-between;border-bottom:3px solid #0E4D8F;margin-bottom:8px}.days{display:grid;gap:8px}.three{grid-template-columns:repeat(3,1fr)}.two{grid-template-columns:repeat(2,1fr)}.day{border:2px solid #d7e6f3;border-radius:18px;padding:8px;background:#f9fcff}.day h3{text-align:center;margin:0 0 8px}.shift{border-radius:14px;padding:6px;margin-bottom:7px}.morning{background:#EAF4FF}.evening{background:#FFF0F4}.morning h4{color:#0E4D8F}.evening h4{color:#C8172B}.company{display:flex;gap:6px;background:white;border-radius:10px;margin:3px 0;padding:4px 6px;font-size:12px}.company b{background:#0E4D8F;color:white;border-radius:50%;min-width:22px;text-align:center}.evening .company b{background:#C8172B}
                </style></head><body>
            """.trimIndent())
            (1..4).forEach { week ->
                val weekStart = state.cycleInfo.currentCycleStart.plusDays(((week - 1) * 7).toLong())
                listOf(0..2, 3..4).forEachIndexed { pageIndex, range ->
                    append("<section class='page'><div class='header'><h1>جداول زيارات صيدلية برج الأطباء</h1><h2>الأسبوع $week - ${if (pageIndex == 0) "السبت إلى الإثنين" else "الثلاثاء والأربعاء"}</h2></div><div class='days ${if (pageIndex == 0) "three" else "two"}'>")
                    range.forEach { dayOffset ->
                        val date = weekStart.plusDays(dayOffset.toLong())
                        append("<div class='day'><h3>${esc(date.dayOfWeek.borgArabicName())}<br><small>${date}</small></h3>")
                        append(listHtml(date, Shift.MORNING)).append(listHtml(date, Shift.EVENING)).append("</div>")
                    }
                    append("</div></section>")
                }
            }
            append("</body></html>")
        }, Charsets.UTF_8)
    }

    private fun writeSchedulesPdf(file: File, state: BorgUiState) {
        val companies = state.companies.associateBy { it.id }
        val currentEpoch = state.cycleInfo.currentCycleStart.toEpochDay()
        val document = PdfDocument()
        val cairo = ResourcesCompat.getFont(this, R.font.cairo_bold) ?: Typeface.DEFAULT_BOLD
        val pageWidth = 842
        val pageHeight = 595
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(14, 77, 143); textSize = 18f; typeface = cairo; textAlign = Paint.Align.RIGHT }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 43, 82); textSize = 8.5f; typeface = cairo; textAlign = Paint.Align.RIGHT }
        fun ellipsize(text: String, max: Int) = if (text.length <= max) text else text.take(max - 1) + "…"
        fun drawShift(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, title: String, visits: List<Visit>, accent: Int, bg: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; style = Paint.Style.FILL }
            canvas.drawRoundRect(x, y, x + w, y + h, 12f, 12f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f; p.color = accent; canvas.drawRoundRect(x, y, x + w, y + h, 12f, 12f, p)
            canvas.drawText("$title (${visits.size})", x + w - 8f, y + 16f, Paint(textPaint).apply { color = accent; textSize = 11f; typeface = Typeface.DEFAULT_BOLD })
            var cy = y + 30f
            visits.forEachIndexed { index, visit ->
                if (cy > y + h - 8f) return@forEachIndexed
                canvas.drawText("${index + 1}. ${ellipsize(companies[visit.companyId]?.name ?: "شركة غير معروفة", 24)}", x + w - 8f, cy, textPaint)
                cy += 16f
            }
        }
        var pageNo = 1
        (1..4).forEach { week ->
            val weekStart = state.cycleInfo.currentCycleStart.plusDays(((week - 1) * 7).toLong())
            listOf(0..2, 3..4).forEachIndexed { pageIndex, range ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo++).create())
                val c = page.canvas
                c.drawColor(Color.WHITE)
                c.drawText("جداول زيارات صيدلية برج الأطباء", pageWidth - 32f, 42f, titlePaint)
                c.drawText("الأسبوع $week - ${if (pageIndex == 0) "السبت / الأحد / الإثنين" else "الثلاثاء / الأربعاء"}", 390f, 42f, Paint(titlePaint).apply { textAlign = Paint.Align.CENTER; textSize = 14f })
                val cols = range.count(); val gap = 10f; val left = 30f; val top = 65f; val colW = (pageWidth - 60f - (cols - 1) * gap) / cols; val colH = pageHeight - 92f
                range.forEachIndexed { i, dayOffset ->
                    val x = left + i * (colW + gap); val date = weekStart.plusDays(dayOffset.toLong())
                    c.drawText(date.dayOfWeek.borgArabicName(), x + colW / 2, top + 18f, Paint(titlePaint).apply { textAlign = Paint.Align.CENTER; textSize = 13f; color = Color.rgb(8,43,82) })
                    val dayVisits = state.visits.filter { it.cycleStartEpochDay == currentEpoch && it.date == date }
                    drawShift(c, x, top + 32f, colW, (colH - 38f) / 2f, "الصباح", dayVisits.filter { it.shift == Shift.MORNING }.scheduleDisplaySorted(), Color.rgb(14,77,143), Color.rgb(234,244,255))
                    drawShift(c, x, top + 38f + (colH - 38f) / 2f, colW, (colH - 38f) / 2f, "المساء", dayVisits.filter { it.shift == Shift.EVENING }.scheduleDisplaySorted(), Color.rgb(200,23,43), Color.rgb(255,240,244))
                }
                document.finishPage(page)
            }
        }
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private fun List<Visit>.scheduleDisplaySorted(): List<Visit> = sortedWith(compareBy<Visit> { it.createdAt }.thenBy { it.id })

    private fun shareLatestBackupToDrive() {
        val container = (application as BorgPharmacyApplication).container
        lifecycleScope.launch {
            val file = container.backupService.dumpDatabase("drive")
            val uri = container.backupService.uriFor(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية قاعدة بيانات صيدلية برج الأطباء")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "نسخ احتياطي سحابي عبر Google Drive"))
        }
    }

    private fun requestBackupStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val uri = Uri.parse("package:$packageName")
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                2604,
            )
        }
    }

    private fun restartApplication() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
