package com.borgpharmacy

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
                onShareToday = { shareTodayStories(state) },
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

    private fun shareTodayStories(state: BorgUiState) {
        val currentEpoch = state.cycleInfo.currentCycleStart.toEpochDay()
        val today = state.cycleInfo.today
        val companies = state.companies.associateBy { it.id }
        val todayVisits = state.visits.filter { it.cycleStartEpochDay == currentEpoch && it.date == today }
        val morning = todayVisits.filter { it.shift == Shift.MORNING }.scheduleDisplaySorted().mapNotNull { companies[it.companyId]?.name }
        val evening = todayVisits.filter { it.shift == Shift.EVENING }.scheduleDisplaySorted().mapNotNull { companies[it.companyId]?.name }

        val shareDir = File(cacheDir, "story_shares").apply { mkdirs() }
        val morningFile = File(shareDir, "borg_morning_story.png")
        val eveningFile = File(shareDir, "borg_evening_story.png")
        createStoryBitmap(
            dateText = today.dayOfWeek.borgArabicName() + " - " + today.toString(),
            weekText = "الأسبوع ${state.cycleInfo.weekOfCycle}",
            shiftTitle = "الفترة الصباحية",
            shiftIcon = "☀️",
            companies = morning,
            accent = Color.rgb(14, 101, 168),
            soft = Color.rgb(234, 244, 255),
            file = morningFile,
        )
        createStoryBitmap(
            dateText = today.dayOfWeek.borgArabicName() + " - " + today.toString(),
            weekText = "الأسبوع ${state.cycleInfo.weekOfCycle}",
            shiftTitle = "الفترة المسائية",
            shiftIcon = "🌙",
            companies = evening,
            accent = Color.rgb(200, 23, 69),
            soft = Color.rgb(255, 240, 245),
            file = eveningFile,
        )
        val uris = arrayListOf(
            (application as BorgPharmacyApplication).container.backupService.uriFor(morningFile),
            (application as BorgPharmacyApplication).container.backupService.uriFor(eveningFile),
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, "جداول زيارات اليوم - صيدلية برج الأطباء")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "مشاركة كحالة واتساب"))
    }

    private fun createStoryBitmap(
        dateText: String,
        weekText: String,
        shiftTitle: String,
        shiftIcon: String,
        companies: List<String>,
        accent: Int,
        soft: Int,
        file: File,
    ) {
        val width = 1080
        val height = 1920
        val root = layoutInflater.inflate(R.layout.share_story_schedule, null)
        root.layoutParams = ViewGroup.LayoutParams(width, height)
        val header = root.findViewById<LinearLayout>(R.id.story_header)
        header.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(36, 91, 199), Color.rgb(47, 145, 241))).apply { cornerRadius = 46f }
        root.findViewById<TextView>(R.id.story_date).text = dateText
        root.findViewById<TextView>(R.id.story_week).text = weekText
        root.findViewById<TextView>(R.id.story_shift_title).text = shiftTitle
        root.findViewById<TextView>(R.id.story_shift_icon).text = shiftIcon
        val list = root.findViewById<LinearLayout>(R.id.story_company_list)
        list.removeAllViews()
        companies.forEachIndexed { index, name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 28f
                    setStroke(4, accent)
                }
                setPadding(26, 18, 26, 18)
            }
            row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 18) }
            val number = TextView(this).apply {
                text = (index + 1).toString()
                setTextColor(accent)
                textSize = 22f
                typeface = Typeface.create(ResourcesCompat.getFont(this@MainActivity, R.font.cairo_bold), Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            }
            number.layoutParams = LinearLayout.LayoutParams(70, 70)
            val title = TextView(this).apply {
                text = name
                setTextColor(Color.rgb(7, 31, 58))
                textSize = if (companies.size > 22) 22f else 28f
                typeface = Typeface.create(ResourcesCompat.getFont(this@MainActivity, R.font.cairo_bold), Typeface.BOLD)
                gravity = android.view.Gravity.RIGHT
                setLineSpacing(0f, 1.05f)
            }
            title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(number)
            row.addView(title)
            list.addView(row)
        }
        val shiftHeader = root.findViewById<LinearLayout>(R.id.story_shift_header)
        shiftHeader.background = GradientDrawable().apply { setColor(soft); cornerRadius = 34f; setStroke(3, accent) }
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
        fun esc(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        val companies = state.companies.associateBy { it.id }
        val currentEpoch = state.cycleInfo.currentCycleStart.toEpochDay()

        fun listHtml(date: java.time.LocalDate, shift: Shift): String {
            val visits = state.visits
                .filter { it.cycleStartEpochDay == currentEpoch && it.date == date && it.shift == shift }
                .scheduleDisplaySorted()
            val densityClass = when {
                visits.size >= 28 -> " ultra-dense"
                visits.size >= 20 -> " very-dense"
                visits.size >= 14 -> " dense"
                else -> ""
            }
            return buildString {
                append("<section class='shift ${if (shift == Shift.MORNING) "morning" else "evening"}$densityClass'>")
                append("<header class='shift-title'><span>${esc(shift.arabicName)}</span><b>${visits.size}</b></header>")
                append("<div class='visit-list'>")
                visits.forEachIndexed { index, visit ->
                    val companyName = companies[visit.companyId]?.name ?: "شركة غير معروفة"
                    append("<article class='company-card'>")
                    append("<span class='index'>${index + 1}</span>")
                    append("<span class='name'>${esc(companyName)}</span>")
                    append("</article>")
                }
                append("</div></section>")
            }
        }

        fun dayHtml(weekStart: java.time.LocalDate, dayOffset: Int): String {
            val date = weekStart.plusDays(dayOffset.toLong())
            return buildString {
                append("<div class='day-column'>")
                append("<h3 class='day-title'>${esc(date.dayOfWeek.borgArabicName())}<br><span>${date}</span></h3>")
                append(listHtml(date, Shift.MORNING))
                append(listHtml(date, Shift.EVENING))
                append("</div>")
            }
        }

        file.writeText(buildString {
            append("""
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>جداول زيارات صيدلية برج الأطباء</title>
                  <style>
                    @page { size: A4 portrait; margin: 10mm; }
                    * { box-sizing: border-box; }
                    html, body { margin: 0; padding: 0; }
                    body {
                      font-family: 'Cairo', 'Tajawal', Tahoma, Arial, sans-serif;
                      background: #e8eef6;
                      color: #082B52;
                      -webkit-print-color-adjust: exact;
                      print-color-adjust: exact;
                    }
                    .print-page {
                      width: 190mm;
                      height: 277mm;
                      margin: 0 auto 12px auto;
                      padding: 0;
                      display: flex;
                      flex-direction: column;
                      overflow: hidden;
                      break-after: page;
                      page-break-after: always;
                      page-break-inside: avoid;
                      background: #f8fafc;
                      border: 1px solid #e2e8f0;
                      border-radius: 14px;
                    }
                    .print-page.first { break-after: page; page-break-after: always; }
                    .print-page:last-child { break-after: auto; page-break-after: auto; }
                    .page-header {
                      flex: 0 0 auto;
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      padding: 0 0 5mm 0;
                      margin: 0 0 4mm 0;
                      border-bottom: 1.2mm solid #0E4D8F;
                      page-break-inside: avoid;
                    }
                    .page-header h1 { margin: 0; font-size: 17pt; font-weight: 900; color: #082B52; line-height: 1.15; }
                    .page-header h2 { margin: 0; font-size: 12pt; font-weight: 900; color: #0E4D8F; line-height: 1.15; }
                    .days-grid {
                      flex: 1 1 auto;
                      min-height: 0;
                      display: flex;
                      gap: 4mm;
                      overflow: hidden;
                    }
                    .days-grid.three .day-column { flex: 1 1 33.333%; }
                    .days-grid.two .day-column { flex: 1 1 50%; }
                    .day-column {
                      min-width: 0;
                      min-height: 0;
                      background: #ffffff;
                      border: 0.35mm solid #e2e8f0;
                      border-radius: 5mm;
                      padding: 3mm;
                      display: flex;
                      flex-direction: column;
                      gap: 3mm;
                      overflow: hidden;
                      page-break-inside: avoid;
                      break-inside: avoid;
                    }
                    .day-title {
                      flex: 0 0 auto;
                      text-align: center;
                      margin: 0;
                      color: #082B52;
                      font-size: 12pt;
                      font-weight: 900;
                      line-height: 1.1;
                      page-break-inside: avoid;
                    }
                    .day-title span { color: #64748b; font-size: 8pt; font-weight: 800; }
                    .shift {
                      flex: 1 1 0;
                      min-height: 0;
                      border-radius: 4mm;
                      padding: 2.2mm;
                      display: flex;
                      flex-direction: column;
                      gap: 1.7mm;
                      overflow: hidden;
                      page-break-inside: avoid;
                      break-inside: avoid;
                    }
                    .morning { background: #EAF4FF; }
                    .evening { background: #FFF0F4; }
                    .shift-title {
                      flex: 0 0 auto;
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      margin: 0;
                      font-size: 9pt;
                      font-weight: 900;
                      line-height: 1.1;
                      page-break-inside: avoid;
                    }
                    .morning .shift-title { color: #0E4D8F; }
                    .evening .shift-title { color: #C8172B; }
                    .shift-title b {
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                      min-width: 7mm;
                      height: 6mm;
                      border-radius: 99px;
                      background: rgba(255,255,255,.78);
                      font-size: 8pt;
                    }
                    .visit-list {
                      flex: 1 1 auto;
                      min-height: 0;
                      display: flex;
                      flex-direction: column;
                      gap: 1.35mm;
                      overflow: hidden;
                    }
                    .company-card {
                      flex: 0 1 auto;
                      display: flex;
                      align-items: center;
                      gap: 1.6mm;
                      width: 100%;
                      background: #ffffff;
                      border-radius: 2.8mm;
                      padding: 1.55mm 1.45mm;
                      color: #082B52;
                      font-size: 8.3pt;
                      font-weight: 900;
                      line-height: 1.2;
                      box-shadow: 0 0.6mm 1.2mm rgba(15,23,42,.06);
                      border-right: 1.1mm solid #cbd5e1;
                      page-break-inside: avoid;
                      break-inside: avoid;
                      overflow: hidden;
                    }
                    .dense .company-card { font-size: 7.3pt; padding: 1.15mm 1.25mm; line-height: 1.12; }
                    .very-dense .company-card { font-size: 6.4pt; padding: .75mm 1.05mm; line-height: 1.06; }
                    .ultra-dense .company-card { font-size: 5.5pt; padding: .48mm .85mm; line-height: 1.0; }
                    .morning .company-card { border-right-color: #0E4D8F; }
                    .evening .company-card { border-right-color: #C8172B; }
                    .index {
                      flex: 0 0 6mm;
                      text-align: center;
                      font-weight: 900;
                      color: #64748b;
                    }
                    .name {
                      flex: 1 1 auto;
                      min-width: 0;
                      white-space: normal;
                      overflow-wrap: anywhere;
                      word-break: normal;
                    }
                    @media print {
                      body { background: #fff; }
                      .print-page { margin: 0; border: none; border-radius: 0; box-shadow: none; }
                    }
                  </style>
                </head>
                <body>
            """.trimIndent())

            (1..4).forEach { week ->
                val weekStart = state.cycleInfo.currentCycleStart.plusDays(((week - 1) * 7).toLong())
                append("<section class='print-page first'><div class='page-header'><h1>جداول زيارات صيدلية برج الأطباء</h1><h2>الأسبوع $week - السبت إلى الإثنين</h2></div><div class='days-grid three'>")
                (0..2).forEach { dayOffset -> append(dayHtml(weekStart, dayOffset)) }
                append("</div></section>")
                append("<section class='print-page'><div class='page-header'><h1>جداول زيارات صيدلية برج الأطباء</h1><h2>الأسبوع $week - الثلاثاء والأربعاء</h2></div><div class='days-grid two'>")
                (3..4).forEach { dayOffset -> append(dayHtml(weekStart, dayOffset)) }
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
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28.35f // 10mm at 72dpi
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 43, 82); textSize = 17f; typeface = cairo; textAlign = Paint.Align.RIGHT }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(14, 77, 143); textSize = 12f; typeface = cairo; textAlign = Paint.Align.LEFT }
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8,43,82); textSize = 12f; typeface = cairo; textAlign = Paint.Align.CENTER }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100,116,139); textSize = 7.5f; typeface = cairo; textAlign = Paint.Align.CENTER }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8,43,82); textSize = 8.2f; typeface = cairo; textAlign = Paint.Align.RIGHT }
        val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100,116,139); textSize = 7.5f; typeface = cairo; textAlign = Paint.Align.CENTER }
        fun ellipsize(text: String, max: Int) = if (text.length <= max) text else text.take(max - 1) + "…"
        fun round(canvas: android.graphics.Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 10f, stroke: Int? = null, strokeWidth: Float = 1f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
            canvas.drawRoundRect(l, t, r, b, radius, radius, p)
            if (stroke != null) { p.style = Paint.Style.STROKE; p.strokeWidth = strokeWidth; p.color = stroke; canvas.drawRoundRect(l, t, r, b, radius, radius, p) }
        }
        fun drawShift(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, title: String, visits: List<Visit>, accent: Int, bg: Int) {
            round(canvas, x, y, x + w, y + h, bg, 9f)
            val headerPaint = Paint(rowPaint).apply { color = accent; textSize = 9f; typeface = cairo }
            canvas.drawText("$title  (${visits.size})", x + w - 7f, y + 14f, headerPaint)
            val available = (h - 24f).coerceAtLeast(12f)
            val rowH = if (visits.isEmpty()) 16f else (available / visits.size).coerceAtMost(16f).coerceAtLeast(4.2f)
            val fontSize = (rowH * 0.50f).coerceAtMost(8.2f).coerceAtLeast(3.6f)
            val dynRowPaint = Paint(rowPaint).apply { textSize = fontSize; typeface = cairo }
            val dynIndexPaint = Paint(indexPaint).apply { textSize = (fontSize * .90f).coerceAtLeast(3.2f); typeface = cairo }
            var cy = y + 23f
            visits.forEachIndexed { index, visit ->
                if (cy >= y + h - 2f) return@forEachIndexed
                val cardTop = cy
                val cardBottom = (cy + rowH * .82f).coerceAtMost(y + h - 2f)
                round(canvas, x + 5f, cardTop, x + w - 5f, cardBottom, Color.WHITE, 6f)
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = 2f }
                canvas.drawLine(x + w - 7f, cardTop + 1f, x + w - 7f, cardBottom - 1f, linePaint)
                val baseline = cardTop + (cardBottom - cardTop) * .66f
                canvas.drawText((index + 1).toString(), x + w - 17f, baseline, dynIndexPaint)
                val maxChars = when { fontSize < 4.5f -> 30; fontSize < 6f -> 26; else -> 23 }
                canvas.drawText(ellipsize(companies[visit.companyId]?.name ?: "شركة غير معروفة", maxChars), x + w - 30f, baseline, dynRowPaint)
                cy += rowH
            }
        }
        fun drawPage(week: Int, title: String, range: IntRange, pageNo: Int) {
            val weekStart = state.cycleInfo.currentCycleStart.plusDays(((week - 1) * 7).toLong())
            val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            val l = margin
            val t = margin
            val r = pageWidth - margin
            val b = pageHeight - margin
            c.drawText("جداول زيارات صيدلية برج الأطباء", r, t + 18f, titlePaint)
            c.drawText(title, l, t + 18f, subtitlePaint)
            val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(14,77,143); strokeWidth = 3f }
            c.drawLine(l, t + 30f, r, t + 30f, underline)
            val cols = range.count()
            val gap = 10f
            val top = t + 44f
            val colH = b - top
            val colW = (r - l - (cols - 1) * gap) / cols
            range.forEachIndexed { i, dayOffset ->
                val x = l + i * (colW + gap)
                val date = weekStart.plusDays(dayOffset.toLong())
                round(c, x, top, x + colW, top + colH, Color.WHITE, 12f, Color.rgb(226,232,240), 1f)
                c.drawText(date.dayOfWeek.borgArabicName(), x + colW / 2, top + 18f, dayPaint)
                c.drawText(date.toString(), x + colW / 2, top + 31f, smallPaint)
                val dayVisits = state.visits.filter { it.cycleStartEpochDay == currentEpoch && it.date == date }
                val shiftTop = top + 43f
                val shiftH = (colH - 50f) / 2f
                drawShift(c, x + 7f, shiftTop, colW - 14f, shiftH, "الفترة الصباحية", dayVisits.filter { it.shift == Shift.MORNING }.scheduleDisplaySorted(), Color.rgb(14,77,143), Color.rgb(234,244,255))
                drawShift(c, x + 7f, shiftTop + shiftH + 8f, colW - 14f, shiftH, "الفترة المسائية", dayVisits.filter { it.shift == Shift.EVENING }.scheduleDisplaySorted(), Color.rgb(200,23,43), Color.rgb(255,240,244))
            }
            document.finishPage(page)
        }
        var pageNo = 1
        (1..4).forEach { week ->
            drawPage(week, "الأسبوع $week - السبت إلى الإثنين", 0..2, pageNo++)
            drawPage(week, "الأسبوع $week - الثلاثاء والأربعاء", 3..4, pageNo++)
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
