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
        
        // دالة فرعية لإنشاء كتل الفترات الصباحية والمسائية بتنسيق مطابق لكروت الشركات بالتطبيق
        fun listHtml(date: java.time.LocalDate, shift: Shift): String {
            val visits = state.visits.filter { it.cycleStartEpochDay == currentEpoch && it.date == date && it.shift == shift }.scheduleDisplaySorted()
            return buildString {
                append("<div class='shift ${if (shift == Shift.MORNING) "morning" else "evening"}'>")
                append("<h4 class='shift-title'>${esc(shift.arabicName)} <span class='badge'>${visits.size}</span></h4>")
                visits.forEachIndexed { index, visit ->
                    val companyName = companies[visit.companyId]?.name ?: "شركة غير معروفة"
                    append("<div class='company-card'>")
                    append("<span class='index'>${index + 1}</span>")
                    append("<span class='name'>${esc(companyName)}</span>")
                    append("</div>")
                }
                append("</div>")
            }
        }
        
        file.writeText(buildString {
            append("""
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                    <meta charset="UTF-8">
                    <title>جداول زيارات صيدلية برج الأطباء</title>
                    <style>
                        /* استيراد الخط المعتمد بالتطبيق مباشرة لضمان انتظام المظهر البصري */
                        @import url('https://fonts.googleapis.com/css2?family=Cairo:wght@400;700;800;900&display=swap');
                        * { box-sizing: border-box; }
                        
                        body {
                            font-family: 'Cairo', Tahoma, sans-serif;
                            margin: 0;
                            padding: 0;
                            background-color: #cbd5e1;
                            -webkit-print-color-adjust: exact;
                        }
                        
                        /* تهيئة دقيقة لمعايير الطباعة والـ PDF من الهاتف لضمان مطابقة الـ A4 */
                        @media print {
                            body { background-color: #fff; }
                            .page {
                                page-break-after: always;
                                box-shadow: none !important;
                                border: none !important;
                                margin: 0 !important;
                                background-color: #fff !important;
                            }
                        }
                        
                        /* بطاقة الصفحة الأساسية ذات قياسات الـ A4 النموذجية */
                        .page {
                            width: 210mm;
                            height: 297mm;
                            padding: 12mm 15mm;
                            background-color: #f8fafc;
                            margin: 20px auto;
                            box-shadow: 0 4px 12px rgba(0,0,0,0.08);
                            border-radius: 16px;
                            display: flex;
                            flex-direction: column;
                            border: 1px solid #e2e8f0;
                        }
                        
                        /* الترويسة العلوية الأنيقة */
                        .header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            border-bottom: 4px solid #0E4D8F;
                            padding-bottom: 10px;
                            margin-bottom: 15px;
                        }
                        .header h1 {
                            font-size: 20px;
                            font-weight: 900;
                            color: #082B52;
                            margin: 0;
                        }
                        .header h2 {
                            font-size: 15px;
                            font-weight: 700;
                            color: #0E4D8F;
                            margin: 0;
                        }
                        
                        /* شبكة التوزيع الثلاثية (السبت، الأحد، الإثنين) */
                        .grid-3 {
                            display: grid;
                            grid-template-columns: repeat(3, 1fr);
                            gap: 12px;
                            flex: 1;
                        }
                        
                        /* شبكة التوزيع الثنائية (الثلاثاء، الأربعاء) */
                        .grid-2 {
                            display: grid;
                            grid-template-columns: repeat(2, 1fr);
                            gap: 20px;
                            flex: 1;
                        }
                        
                        /* أعمدة الأيام */
                        .day-column {
                            background-color: #ffffff;
                            border: 1px solid #e2e8f0;
                            border-radius: 16px;
                            padding: 10px;
                            display: flex;
                            flex-direction: column;
                            gap: 10px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.02);
                        }
                        .day-title {
                            text-align: center;
                            font-size: 15px;
                            font-weight: 900;
                            color: #082B52;
                            margin: 0;
                            line-height: 1.2;
                        }
                        .day-subtitle {
                            font-size: 11px;
                            color: #64748b;
                            font-weight: 700;
                        }
                        
                        /* حاويات الفترات الملونة المريحة للعين */
                        .shift {
                            border-radius: 12px;
                            padding: 8px;
                            flex: 1;
                            display: flex;
                            flex-direction: column;
                            gap: 6px;
                        }
                        .morning { background-color: #EAF4FF; }
                        .evening { background-color: #FFF0F4; }
                        
                        .shift-title {
                            font-size: 12px;
                            font-weight: 850;
                            margin: 0 0 4px 0;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }
                        .morning .shift-title { color: #0E4D8F; }
                        .evening .shift-title { color: #C8172B; }
                        
                        .badge {
                            background-color: rgba(255,255,255,0.7);
                            padding: 1px 6px;
                            border-radius: 50px;
                            font-size: 10px;
                            font-weight: 800;
                        }
                        
                        /* بطاقة الشركة المستوحاة من كروت الشركات بالتطبيق وبشريط جانبي ملون مميز (RTL) */
                        .company-card {
                            background-color: #ffffff;
                            border-radius: 8px;
                            padding: 8px 10px;
                            font-size: 12px;
                            font-weight: 800; /* خط عريض وبارز */
                            color: #082B52;
                            display: flex;
                            align-items: center;
                            gap: 6px;
                            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                            border-right: 4px solid #cbd5e1;
                        }
                        .morning .company-card { border-right-color: #0E4D8F; }
                        .evening .company-card { border-right-color: #C8172B; }
                        
                        .index {
                            font-weight: 900;
                            color: #64748b;
                            width: 14px;
                            text-align: center;
                        }
                        
                        /* خاصية التفاف النص التلقائي لأسفل لكي يظهر اسم الشركة كاملاً في حال كان طويلاً */
                        .name {
                            word-break: break-word;
                            line-height: 1.35;
                        }
                    </style>
                </head>
                <body>
            """.trimIndent())
            
            // التكرار لكل أسابيع الدورة الأربعة لإنشاء صفحات منفصلة
            (1..4).forEach { week ->
                val weekStart = state.cycleInfo.currentCycleStart.plusDays(((week - 1) * 7).toLong())
                
                // الصفحة الأولى: السبت، الأحد، الإثنين
                append("<section class='page'><div class='header'><h1>جداول زيارات صيدلية برج الأطباء</h1><h2>الأسبوع $week - السبت إلى الإثنين</h2></div><div class='grid-3'>")
                (0..2).forEach { dayOffset ->
                    val date = weekStart.plusDays(dayOffset.toLong())
                    append("<div class='day-column'><h3 class='day-title'>${esc(date.dayOfWeek.borgArabicName())}<br><span class='day-subtitle'>${date}</span></h3>")
                    append(listHtml(date, Shift.MORNING))
                    append(listHtml(date, Shift.EVENING))
                    append("</div>")
                }
                append("</div></section>")
                
                // الصفحة الثانية: الثلاثاء، الأربعاء
                append("<section class='page'><div class='header'><h1>جداول زيارات صيدلية برج الأطباء</h1><h2>الأسبوع $week - الثلاثاء والأربعاء</h2></div><div class='grid-2'>")
                (3..4).forEach { dayOffset ->
                    val date = weekStart.plusDays(dayOffset.toLong())
                    append("<div class='day-column'><h3 class='day-title'>${esc(date.dayOfWeek.borgArabicName())}<br><span class='day-subtitle'>${date}</span></h3>")
                    append(listHtml(date, Shift.MORNING))
                    append(listHtml(date, Shift.EVENING))
                    append("</div>")
                }
                append("</div></section>")
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
