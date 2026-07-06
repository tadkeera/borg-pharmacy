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
        shareExportFile(file, "تصدير جداول زيارات صيدلية برج الأطباء")
    }

    private fun shareExportFile(file: File, subject: String) {
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
                        state.visits
                            .filter { it.cycleStartEpochDay == currentEpoch && it.date == date && it.shift == shift }
                            .scheduleDisplaySorted()
                            .forEachIndexed { index, visit ->
                                append(week).append(',')
                                append(csv(date.toString())).append(',')
                                append(csv(visit.date.dayOfWeek.borgArabicName())).append(',')
                                append(csv(visit.shift.arabicName)).append(',')
                                append(index + 1).append(',')
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
        fun shiftHtml(week: Int, date: java.time.LocalDate, shift: Shift): String {
            val visits = state.visits.filter { it.cycleStartEpochDay == currentEpoch && it.date == date && it.shift == shift }.scheduleDisplaySorted()
            return buildString {
                append("<div class='shift ${if (shift == Shift.MORNING) "morning" else "evening"}'><h4>${esc(shift.arabicName)} <span>${visits.size}</span></h4>")
                visits.forEachIndexed { index, visit -> append("<div class='company'><b>${index + 1}</b><span>${esc(companies[visit.companyId]?.name ?: "شركة غير معروفة")}</span></div>") }
                append("</div>")
            }
        }
        file.writeText(buildString {
            append("""
                <!DOCTYPE html><html lang="ar" dir="rtl"><head><meta charset="UTF-8">
                <style>
                @page{size:A4 landscape;margin:7mm}*{box-sizing:border-box}body{font-family:Arial,'Tahoma',sans-serif;margin:0;color:#082B52;background:#eef4fb}.page{width:100%;min-height:190mm;page-break-after:always;background:white;border-radius:18px;padding:10mm;box-shadow:0 4px 18px #0002}.header{display:flex;justify-content:space-between;align-items:center;border-bottom:3px solid #0E4D8F;padding-bottom:6px;margin-bottom:10px}.header h1{margin:0;color:#0E4D8F}.days{display:grid;gap:8px;height:155mm}.days.three{grid-template-columns:repeat(3,1fr)}.days.two{grid-template-columns:repeat(2,1fr)}.day{border:2px solid #d7e6f3;border-radius:18px;padding:8px;background:#f9fcff;overflow:hidden}.day h3{text-align:center;margin:0 0 8px;font-size:18px;color:#082B52}.shift{border-radius:14px;padding:6px;margin-bottom:7px}.morning{background:#EAF4FF;border:1px solid #b7d8f1}.evening{background:#FFF0F4;border:1px solid #f2bfd0}.shift h4{margin:0 0 5px;font-size:15px}.morning h4{color:#0E4D8F}.evening h4{color:#C8172B}.company{display:flex;align-items:center;gap:5px;background:white;border-radius:10px;margin:3px 0;padding:4px 6px;font-size:12px;min-height:22px}.company b{display:inline-flex;align-items:center;justify-content:center;min-width:22px;height:22px;border-radius:50%;color:white;background:#0E4D8F}.evening .company b{background:#C8172B}.company span{line-height:1.25}.footer{position:absolute;bottom:8mm;left:12mm;color:#697386;font-size:11px}
                </style><title>جداول زيارات صيدلية برج الأطباء</title></head><body>
            """.trimIndent())
            (1..4).forEach { week ->
                val weekStart = state.cycleInfo.currentCycleStart.plusDays(((week - 1) * 7).toLong())
                listOf(0..2, 3..4).forEachIndexed { pageIndex, range ->
                    append("<section class='page'><div class='header'><h1>جداول زيارات صيدلية برج الأطباء</h1><h2>الأسبوع $week - ${if (pageIndex == 0) "السبت إلى الإثنين" else "الثلاثاء والأربعاء"}</h2></div>")
                    append("<div class='days ${if (pageIndex == 0) "three" else "two"}'>")
                    range.forEach { dayOffset ->
                        val date = weekStart.plusDays(dayOffset.toLong())
                        append("<div class='day'><h3>${esc(date.dayOfWeek.borgArabicName())}<br><small>${esc(date.toString())}</small></h3>")
                        append(shiftHtml(week, date, Shift.MORNING))
                        append(shiftHtml(week, date, Shift.EVENING))
                        append("</div>")
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
        val pageWidth = 842
        val pageHeight = 595
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(14, 77, 143); textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.RIGHT }
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 43, 82); textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 43, 82); textSize = 8.5f; textAlign = Paint.Align.RIGHT }
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 8f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        fun ellipsize(text: String, max: Int) = if (text.length <= max) text else text.take(max - 1) + "…"
        fun drawRoundRect(canvas: android.graphics.Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int, stroke: Int? = null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
            canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, p)
            if (stroke != null) { p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.color = stroke; canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, p) }
        }
        fun drawShift(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, title: String, visits: List<Visit>, accent: Int, bg: Int) {
            drawRoundRect(canvas, x, y, x + w, y + h, bg, accent)
            val hp = Paint(headPaint).apply { color = accent; textSize = 11f }
            canvas.drawText("$title (${visits.size})", x + w / 2, y + 16f, hp)
            var cy = y + 29f
            visits.forEachIndexed { index, visit ->
                if (cy > y + h - 8f) return@forEachIndexed
                drawRoundRect(canvas, x + 6f, cy - 10f, x + w - 6f, cy + 6f, Color.WHITE, null)
                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.FILL }
                canvas.drawCircle(x + w - 18f, cy - 2f, 7f, circlePaint)
                canvas.drawText((index + 1).toString(), x + w - 18f, cy + 1f, numPaint)
                canvas.drawText(ellipsize(companies[visit.companyId]?.name ?: "شركة غير معروفة", 24), x + w - 30f, cy + 1f, smallPaint)
                cy += 18f
            }
        }
        var pageNo = 1
        (1..4).forEach { week ->
            val weekStart = state.cycleInfo.currentCycleStart.plusDays(((week - 1) * 7).toLong())
            listOf(0..2, 3..4).forEachIndexed { pageIndex, range ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo++).create())
                val c = page.canvas
                drawRoundRect(c, 18f, 18f, pageWidth - 18f, pageHeight - 18f, Color.WHITE, Color.rgb(210, 226, 239))
                c.drawText("جداول زيارات صيدلية برج الأطباء", pageWidth - 35f, 45f, titlePaint)
                c.drawText("الأسبوع $week - ${if (pageIndex == 0) "السبت / الأحد / الإثنين" else "الثلاثاء / الأربعاء"}", 320f, 45f, Paint(headPaint).apply { textSize = 15f })
                val gap = 10f
                val cols = range.count()
                val left = 34f
                val top = 70f
                val colW = (pageWidth - 68f - (cols - 1) * gap) / cols
                val colH = pageHeight - 105f
                range.forEachIndexed { i, dayOffset ->
                    val x = left + i * (colW + gap)
                    val date = weekStart.plusDays(dayOffset.toLong())
                    drawRoundRect(c, x, top, x + colW, top + colH, Color.rgb(249, 252, 255), Color.rgb(183, 216, 241))
                    c.drawText(date.dayOfWeek.borgArabicName(), x + colW / 2, top + 21f, Paint(headPaint).apply { textSize = 14f })
                    c.drawText(date.toString(), x + colW / 2, top + 38f, Paint(headPaint).apply { textSize = 9f; color = Color.rgb(105, 115, 134) })
                    val dayVisits = state.visits.filter { it.cycleStartEpochDay == currentEpoch && it.date == date }
                    val morning = dayVisits.filter { it.shift == Shift.MORNING }.scheduleDisplaySorted()
                    val evening = dayVisits.filter { it.shift == Shift.EVENING }.scheduleDisplaySorted()
                    val shiftH = (colH - 56f) / 2f
                    drawShift(c, x + 8f, top + 48f, colW - 16f, shiftH, "الصباح", morning, Color.rgb(14, 77, 143), Color.rgb(234, 244, 255))
                    drawShift(c, x + 8f, top + 54f + shiftH, colW - 16f, shiftH, "المساء", evening, Color.rgb(200, 23, 43), Color.rgb(255, 240, 244))
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
