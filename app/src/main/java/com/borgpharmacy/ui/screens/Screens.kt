@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borgpharmacy.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borgpharmacy.R
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.Shift
import com.borgpharmacy.domain.Tier
import com.borgpharmacy.domain.UserRole
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.VisitStatus
import com.borgpharmacy.domain.borgArabicName
import com.borgpharmacy.domain.isBorgWorkingDay
import com.borgpharmacy.ui.BorgUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val arabicLocale = Locale("ar")
private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
private val arabicLongDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", arabicLocale)
private val arabicMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", arabicLocale)

private val BorgBlue = Color(0xFF0E4D8F)
private val BorgRed = Color(0xFFC8172B)
private val DeepNavy = Color(0xFF082B52)
private val SoftBlue = Color(0xFFEAF4FF)
private val SoftRed = Color(0xFFFFEEF1)
private val SoftGreen = Color(0xFFEAFBF1)
private val SoftAmber = Color(0xFFFFF7E6)

private enum class Route(val label: String, val icon: ImageVector) {
    HOME("اليوم", Icons.Default.Home),
    WEEKLY("الأسابيع", Icons.Default.CalendarMonth),
    COMPANIES("الشركات", Icons.Default.Business),
    ENQUIRIES("التواصل", Icons.Default.Send),
    BOT("بوت واتساب", Icons.Default.Sync),
    DASHBOARD("التقارير", Icons.Default.Assessment),
    SETTINGS("الإعدادات", Icons.Default.Settings),
}

@Composable
fun BorgApp(
    state: BorgUiState,
    onLogin: (String, String) -> Unit,
    onChangeForcedPasscode: (String) -> Unit,
    onLogout: () -> Unit,
    onAddCompany: (String) -> Unit,
    onImportCsv: () -> Unit,
    onExportCompanies: (String) -> Unit,
    onExportSchedules: (String) -> Unit,
    onUpdateCompanyName: (String, String) -> Unit,
    onDeleteCompany: (String) -> Unit,
    onDeleteAllCompanies: () -> Unit,
    onAddRepresentative: (String, String, String) -> Unit,
    onDeleteRepresentative: (String) -> Unit,
    onCreateUser: (String, String, UserRole, String) -> Unit,
    onMarkVisitStatus: (String, VisitStatus) -> Unit,
    onPrint: (Company, Representative, Visit) -> Unit,
    onWhatsApp: (Company, Representative) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDriveBackup: () -> Unit,
    onSync: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            val message = state.message ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message)
            onDismissMessage()
        }

        if (!state.initialized) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@CompositionLocalProvider
        }

        if (!state.isUnlocked) {
            LockScreen(
                state = state,
                onLogin = onLogin,
                onChangeForcedPasscode = onChangeForcedPasscode,
            )
            return@CompositionLocalProvider
        }

        var route by rememberSaveable { mutableStateOf(Route.HOME.name) }
        val selected = Route.valueOf(route)
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = { BorgTopBar(state = state, onLogout = onLogout) },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    Route.entries.forEach { item ->
                        NavigationBarItem(
                            selected = selected == item,
                            onClick = { route = item.name },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            },
        ) { padding ->
            val contentModifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF7FAFE))
            when (selected) {
                Route.HOME -> HomeScreen(state, onPrint, onMarkVisitStatus, onSync, contentModifier)
                Route.WEEKLY -> WeeklyScreen(state, onExportSchedules, contentModifier)
                Route.COMPANIES -> CompanyProfilesScreen(
                    state = state,
                    onAddCompany = onAddCompany,
                    onImportCsv = onImportCsv,
                    onExportCompanies = onExportCompanies,
                    onUpdateCompanyName = onUpdateCompanyName,
                    onDeleteCompany = onDeleteCompany,
                    onDeleteAllCompanies = onDeleteAllCompanies,
                    onAddRepresentative = onAddRepresentative,
                    onDeleteRepresentative = onDeleteRepresentative,
                    modifier = contentModifier,
                )
                Route.ENQUIRIES -> EnquiriesScreen(state, onWhatsApp, contentModifier)
                Route.BOT -> WhatsAppBotScreen(state = state, modifier = contentModifier)
                Route.DASHBOARD -> DashboardScreen(state, contentModifier)
                Route.SETTINGS -> SettingsScreen(
                    state = state,
                    onBackup = onBackup,
                    onRestore = onRestore,
                    onDriveBackup = onDriveBackup,
                    onSync = onSync,
                    onCreateUser = onCreateUser,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BorgTopBar(state: BorgUiState, onLogout: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = DeepNavy,
            actionIconContentColor = DeepNavy,
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(painter = painterResource(R.drawable.borg_logo), contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "صيدلية برج الأطباء",
                    fontWeight = FontWeight.ExtraBold,
                    color = DeepNavy,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 22.sp,
                    maxLines = 1,
                )
            }
        },
        actions = {
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "تسجيل الخروج") }
        },
    )
}

@Composable
private fun LockScreen(
    state: BorgUiState,
    onLogin: (String, String) -> Unit,
    onChangeForcedPasscode: (String) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("admin") }
    var passcode by rememberSaveable { mutableStateOf("") }
    var newPasscode by rememberSaveable { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, SoftBlue)))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painter = painterResource(R.drawable.borg_logo), contentDescription = null, modifier = Modifier.size(130.dp))
                Text("تفعيل نظام صيدلية برج الأطباء", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = DeepNavy)
                if (state.mustChangePasscodeUser == null) {
                    OutlinedTextField(
        colors = borgTextFieldColors(),value = username, onValueChange = { username = it }, label = { Text("اسم المستخدم") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
        colors = borgTextFieldColors(),value = passcode, onValueChange = { passcode = it }, label = { Text("كود التفعيل / كلمة المرور") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onLogin(username, passcode) }, modifier = Modifier.fillMaxWidth()) { Text("دخول") }
                    Text("الكود الافتراضي للمدير: admin2026 ويجب تغييره فورًا عند أول دخول", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                } else {
                    Text("لأمان النظام يجب تغيير كود المدير الافتراضي الآن.", fontWeight = FontWeight.SemiBold, color = BorgRed)
                    OutlinedTextField(
        colors = borgTextFieldColors(),value = newPasscode, onValueChange = { newPasscode = it }, label = { Text("الكود الجديد") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onChangeForcedPasscode(newPasscode) }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الكود الجديد") }
                }
                state.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: BorgUiState,
    onPrint: (Company, Representative, Visit) -> Unit,
    onMarkVisitStatus: (String, VisitStatus) -> Unit,
    onSync: () -> Unit,
    modifier: Modifier,
) {
    val today = state.cycleInfo.today
    val companies = state.companies.associateBy { it.id }
    val currentCycleEpoch = state.cycleInfo.currentCycleStart.toEpochDay()
    val visitsToday = state.visits.filter { it.cycleStartEpochDay == currentCycleEpoch && it.date == today && companies.containsKey(it.companyId) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            HomeHeroCard(
                date = today,
                cycleStart = state.cycleInfo.currentCycleStart,
                cycleEnd = state.cycleInfo.currentCycleEnd,
                week = state.cycleInfo.weekOfCycle,
                totalVisits = visitsToday.size,
                onSync = onSync,
            )
        }
        item {
            CreativeShiftTable(
                title = "الفترة الصباحية",
                subtitle = "استقبال مندوبي الشركات في الفترة الصباحية",
                accent = BorgBlue,
                softAccent = SoftBlue,
                visits = visitsToday.filter { it.shift == Shift.MORNING }.displaySorted(),
                companies = companies,
                state = state,
                onPrint = onPrint,
                onMarkVisitStatus = onMarkVisitStatus,
            )
        }
        item {
            CreativeShiftTable(
                title = "الفترة المسائية",
                subtitle = "تنظيم زيارات المساء بسلاسة ووضوح",
                accent = BorgRed,
                softAccent = SoftRed,
                visits = visitsToday.filter { it.shift == Shift.EVENING }.displaySorted(),
                companies = companies,
                state = state,
                onPrint = onPrint,
                onMarkVisitStatus = onMarkVisitStatus,
            )
        }
    }
}

@Composable
private fun HomeHeroCard(
    date: LocalDate,
    cycleStart: LocalDate,
    cycleEnd: LocalDate,
    week: Int,
    totalVisits: Int,
    onSync: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF245BC7), Color(0xFF2F91F1))), RoundedCornerShape(28.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                date.format(arabicLongDateFormatter),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("جدول زيارات اليوم", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassPill("الأسبوع $week")
                    GlassPill("$totalVisits زيارة")
                    IconButton(onClick = onSync) { Icon(Icons.Default.Sync, contentDescription = "مزامنة", tint = Color.White) }
                }
                Text("الدورة: من ${cycleStart.format(shortDateFormatter)} إلى ${cycleEnd.format(shortDateFormatter)}", color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GlassPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CreativeShiftTable(
    title: String,
    subtitle: String,
    accent: Color,
    softAccent: Color,
    visits: List<Visit>,
    companies: Map<String, Company>,
    state: BorgUiState,
    onPrint: (Company, Representative, Visit) -> Unit,
    onMarkVisitStatus: (String, VisitStatus) -> Unit,
) {
    var expandedVisitId by rememberSaveable { mutableStateOf<String?>(null) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(62.dp).clip(CircleShape).background(softAccent), contentAlignment = Alignment.Center) {
                    Text(if (title.contains("الصباحية")) "☀️" else "🌙", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF071F3A))
                    Text("${visits.size} شركة", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF697386), fontWeight = FontWeight.Bold)
                }
            }

            if (visits.isEmpty()) {
                EmptyState("لا توجد شركات مجدولة في هذه الفترة اليوم.")
            }

            visits.forEach { visit ->
                val company = companies[visit.companyId] ?: return@forEach
                val expanded = expandedVisitId == visit.id
                val reps = state.repsByCompany[company.id].orEmpty()
                VisitTableRow(
                    visit = visit,
                    company = company,
                    reps = reps,
                    expanded = expanded,
                    accent = accent,
                    state = state,
                    onExpand = { expandedVisitId = if (expanded) null else visit.id },
                    onPrint = onPrint,
                    onMarkVisitStatus = onMarkVisitStatus,
                )
            }
        }
    }
}

@Composable
private fun ShiftTableHeader(accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("اسم الشركة", modifier = Modifier.weight(1f), color = accent, fontWeight = FontWeight.Bold)
        Text("الطباعة", modifier = Modifier.width(72.dp), color = accent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VisitTableRow(
    visit: Visit,
    company: Company,
    reps: List<Representative>,
    expanded: Boolean,
    accent: Color,
    state: BorgUiState,
    onExpand: () -> Unit,
    onPrint: (Company, Representative, Visit) -> Unit,
    onMarkVisitStatus: (String, VisitStatus) -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onExpand() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(2.dp, accent.copy(alpha = 0.78f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = accent)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        company.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Right,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.width(7.dp).height(58.dp).clip(RoundedCornerShape(50)).background(accent))
                }
            }

            if (expanded) {
                HorizontalDivider(color = accent.copy(alpha = 0.15f))
                if (reps.isEmpty()) {
                    Text("لا يوجد مندوبون مسجلون لهذه الشركة.", color = Color(0xFF697386))
                }
                reps.forEach { rep ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(rep.name, fontWeight = FontWeight.Bold, color = DeepNavy)
                            Text(rep.phone, style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                        }
                        Text("تمت الطباعة: ${state.printCountMap[rep.id to visit.id] ?: 0}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF697386))
                        IconButton(onClick = { onPrint(company, rep, visit) }) { Icon(Icons.Default.Print, contentDescription = "طباعة تصريح", tint = accent) }
                    }
                }
                if (state.isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onMarkVisitStatus(visit.id, VisitStatus.COMPLETED) }) { Text("تأكيد الزيارة") }
                        OutlinedButton(onClick = { onMarkVisitStatus(visit.id, VisitStatus.MISSED) }) { Text("لم يحضر") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyScreen(state: BorgUiState, onExportSchedules: (String) -> Unit, modifier: Modifier) {
    var selectedWeek by rememberSaveable { mutableStateOf(1) }
    val companies = state.companies.associateBy { it.id }
    val currentCycleEpoch = state.cycleInfo.currentCycleStart.toEpochDay()
    Column(modifier.padding(16.dp)) {
        HeaderCard(
            title = "جداول الزيارات الأسبوعية",
            subtitle = "عرض احترافي لكل أسبوع مع توزيع متوازن يستوعب أكثر من 400 شركة دوائية عند الحاجة.",
            gradient = listOf(DeepNavy, BorgBlue),
        )
        Spacer(Modifier.height(8.dp))
        ExportDropdown(label = "تصدير", onExport = onExportSchedules)
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = selectedWeek - 1, containerColor = Color.White, contentColor = BorgBlue) {
            (1..4).forEach { week ->
                Tab(
                    selected = selectedWeek == week,
                    onClick = { selectedWeek = week },
                    text = { Text("الأسبوع $week", fontWeight = if (selectedWeek == week) FontWeight.ExtraBold else FontWeight.Medium) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            val weekStart = state.cycleInfo.currentCycleStart.plusDays(((selectedWeek - 1) * 7).toLong())
            val days = (0..6).map { weekStart.plusDays(it.toLong()) }.filter { it.dayOfWeek.isBorgWorkingDay() }
            items(days) { day ->
                val visits = state.visits.filter { it.cycleStartEpochDay == currentCycleEpoch && it.date == day }
                WeeklyDayCard(day, visits, companies)
            }
        }
    }
}

@Composable
private fun WeeklyDayCard(day: LocalDate, visits: List<Visit>, companies: Map<String, Company>) {
    val morning = visits.filter { it.shift == Shift.MORNING }.displaySorted()
    val evening = visits.filter { it.shift == Shift.EVENING }.displaySorted()
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.dayOfWeek.borgArabicName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = DeepNavy)
                    Text(day.format(arabicLongDateFormatter), color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = {}, label = { Text("${visits.size} زيارة") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeeklyShiftPanel("الصباح", morning, companies, BorgBlue, SoftBlue, Modifier.weight(1f))
                WeeklyShiftPanel("المساء", evening, companies, BorgRed, SoftRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeeklyShiftPanel(title: String, visits: List<Visit>, companies: Map<String, Company>, accent: Color, softAccent: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(softAccent)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.18f)), RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = accent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${visits.size}", color = accent, fontWeight = FontWeight.ExtraBold)
        }
        if (visits.isEmpty()) {
            Text("لا توجد زيارات", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
        }
        visits.forEachIndexed { index, visit ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.80f))
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                Text(companies[visit.companyId]?.name ?: "شركة غير معروفة", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = DeepNavy)
            }
        }
    }
}

@Composable
private fun CompanyProfilesScreen(
    state: BorgUiState,
    onAddCompany: (String) -> Unit,
    onImportCsv: () -> Unit,
    onExportCompanies: (String) -> Unit,
    onUpdateCompanyName: (String, String) -> Unit,
    onDeleteCompany: (String) -> Unit,
    onDeleteAllCompanies: () -> Unit,
    onAddRepresentative: (String, String, String) -> Unit,
    onDeleteRepresentative: (String) -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var newCompany by rememberSaveable { mutableStateOf("") }
    var showDeleteAllConfirm by rememberSaveable { mutableStateOf(false) }
    val filtered = state.companies.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("تأكيد حذف جميع الشركات") },
            text = { Text("سيتم حذف جميع الشركات والمندوبين وجميع الزيارات المرتبطة بها. لا يتم تحريك أي جدول آخر. إذا رغبت بالاحتفاظ بنسخة، اضغط زر النسخ الاحتياطي اليدوي قبل الحذف. هل تريد المتابعة؟") },
            confirmButton = {
                Button(onClick = { showDeleteAllConfirm = false; onDeleteAllCompanies() }) { Text("نعم، حذف الكل") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllConfirm = false }) { Text("تراجع") }
            },
        )
    }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderCard("ملفات الشركات", "بحث ذكي باللغة العربية والإنجليزية، واستيراد CSV، وإدارة مندوبي كل شركة.", listOf(BorgBlue, Color(0xFF3A93D8))) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBox(query, { query = it }, "ابحث عن شركة")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
        colors = borgTextFieldColors(),value = newCompany, onValueChange = { newCompany = it }, label = { Text("اسم شركة جديدة") }, enabled = state.isAdmin, modifier = Modifier.weight(1f))
                    Button(onClick = { onAddCompany(newCompany); newCompany = "" }, enabled = state.isAdmin) { Text("إضافة") }
                }
                OutlinedButton(onClick = onImportCsv, enabled = state.isAdmin) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("استيراد CSV") }
                ExportDropdown(label = "تصدير", onExport = onExportCompanies)
                OutlinedButton(onClick = { showDeleteAllConfirm = true }, enabled = state.isAdmin && state.companies.isNotEmpty()) {
                    Icon(Icons.Default.Delete, null, tint = BorgRed)
                    Spacer(Modifier.width(6.dp))
                    Text("حذف الكل", color = BorgRed)
                }
                if (query.length in 1..2) Text("اكتب 3 أحرف لعرض الاقتراحات الذكية.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                if (query.length >= 3) Text("تم العثور على ${filtered.size} نتيجة", style = MaterialTheme.typography.bodySmall, color = BorgBlue)
            }
        }
        items(filtered, key = { it.id }) { company ->
            CompanyProfileCard(company, state, onUpdateCompanyName, onDeleteCompany, onAddRepresentative, onDeleteRepresentative)
        }
    }
}

@Composable
private fun CompanyProfileCard(
    company: Company,
    state: BorgUiState,
    onUpdateCompanyName: (String, String) -> Unit,
    onDeleteCompany: (String) -> Unit,
    onAddRepresentative: (String, String, String) -> Unit,
    onDeleteRepresentative: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showEditNamePopup by rememberSaveable(company.id) { mutableStateOf(false) }
    var editedName by rememberSaveable(company.id) { mutableStateOf(company.name) }
    var showDeleteConfirm by rememberSaveable(company.id) { mutableStateOf(false) }
    var showRepPopup by rememberSaveable(company.id) { mutableStateOf(false) }
    var repName by rememberSaveable(company.id) { mutableStateOf("") }
    var repPhone by rememberSaveable(company.id) { mutableStateOf("+967") }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد حذف الشركة") },
            text = { Text("هل أنت متأكد من حذف شركة ${company.name}؟ سيتم حذف جميع زياراتها فقط دون تغيير ترتيب بقية الجدول.") },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = false; onDeleteCompany(company.id) }) { Text("نعم، حذف") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("تراجع") }
            },
        )
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showEditNamePopup) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftBlue),
                    border = BorderStroke(1.dp, BorgBlue.copy(alpha = 0.25f)),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعديل اسم الشركة", color = DeepNavy, fontWeight = FontWeight.ExtraBold)
                        OutlinedTextField(
                            colors = borgTextFieldColors(),
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("اسم الشركة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onUpdateCompanyName(company.id, editedName); showEditNamePopup = false }) { Text("حفظ التعديلات") }
                            OutlinedButton(onClick = { editedName = company.name; showEditNamePopup = false }) { Text("رجوع") }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(company.name, fontWeight = FontWeight.ExtraBold, color = DeepNavy)
                    Text("المعرف ثابت: ${company.id.take(8)} • ${company.tier.arabicLabel()}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                }
                TextButton(onClick = { editedName = company.name; showEditNamePopup = true }, enabled = state.isAdmin) { Text("تعديل") }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "إخفاء المندوبين" else "المندوبون") }
                IconButton(onClick = { showDeleteConfirm = true }, enabled = state.isAdmin) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
            }
            if (expanded) {
                val reps = state.repsByCompany[company.id].orEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { showRepPopup = true }, enabled = state.isAdmin) { Text("إضافة مندوب") }
                    Text("عدد المندوبين: ${reps.size}", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
                }

                if (showRepPopup) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFF)),
                        border = BorderStroke(1.dp, BorgBlue.copy(alpha = 0.20f)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("إضافة مندوب لشركة ${company.name}", color = DeepNavy, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                colors = borgTextFieldColors(),
                                value = repName,
                                onValueChange = { repName = it },
                                label = { Text("اسم المندوب") },
                                enabled = state.isAdmin,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                colors = borgTextFieldColors(),
                                value = repPhone,
                                onValueChange = { repPhone = it },
                                label = { Text("رقم المندوب") },
                                enabled = state.isAdmin,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onAddRepresentative(company.id, repName, repPhone)
                                        repName = ""
                                        repPhone = "+967"
                                    },
                                    enabled = state.isAdmin,
                                ) { Text("حفظ") }
                                OutlinedButton(onClick = { showRepPopup = false }) { Text("رجوع إلى القائمة") }
                            }
                        }
                    }
                }

                reps.forEach { rep ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${rep.name} • ${rep.phone}", modifier = Modifier.weight(1f), color = DeepNavy)
                        IconButton(onClick = { onDeleteRepresentative(rep.id) }, enabled = state.isAdmin) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnquiriesScreen(
    state: BorgUiState,
    onWhatsApp: (Company, Representative) -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = state.companies.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderCard("الاستعلامات والتواصل", "إرسال جدول الزيارات عبر واتساب لكل مندوب برسالة عربية منظمة دون عرض ترتيب اليوم داخل الدورة.", listOf(Color(0xFF128C7E), Color(0xFF25D366))) }
        item { SearchBox(query, { query = it }, "ابحث عن شركة") }
        items(filtered, key = { it.id }) { company ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(company.name, fontWeight = FontWeight.ExtraBold, color = DeepNavy)
                    val reps = state.repsByCompany[company.id].orEmpty()
                    if (reps.isEmpty()) Text("لا يوجد مندوبون مسجلون.", color = Color(0xFF697386))
                    reps.forEach { rep ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(rep.name, fontWeight = FontWeight.Bold, color = DeepNavy)
                                Text(rep.phone, style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                            }
                            IconButton(onClick = { onWhatsApp(company, rep) }) { Icon(Icons.Default.Send, contentDescription = "واتساب", tint = Color(0xFF128C7E)) }
                        }
                    }
                }
            }
        }
    }
}

data class BotLog(
    val id: String,
    val senderPhone: String,
    val queryText: String,
    val matchedCompany: String,
    val createdAt: String,
)

@Composable
private fun WhatsAppBotScreen(state: BorgUiState, modifier: Modifier) {
    var botPhone by rememberSaveable { mutableStateOf("967") }
    var botActive by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf("لم يتم إرسال كود ربط بعد") }
    val logs = remember { mutableStateListOf<BotLog>() }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            HeaderCard(
                title = "بوت واتساب",
                subtitle = "إعداد رقم البوت ومتابعة سجل استعلامات مندوبي الشركات.",
                gradient = listOf(Color(0xFF128C7E), Color(0xFF25D366)),
            )
        }
        item {
            BotSettingsScreen(
                currentPhone = botPhone,
                isActive = botActive,
                canEdit = state.isAdmin,
                statusMessage = statusMessage,
                onSaveSettings = { phone, active ->
                    val cleanPhone = phone.filter { it.isDigit() }.ifBlank { "967" }
                    botPhone = cleanPhone
                    botActive = active
                    statusMessage = if (active) "تم حفظ الرقم $cleanPhone، وسيطلب البوت كود ربط جديد لهذا الرقم." else "تم حفظ الرقم $cleanPhone مع إيقاف البوت مؤقتًا."
                },
            )
        }
        item { BotLogsReportScreen(logs = logs) }
    }
}

@Composable
fun BotSettingsScreen(
    currentPhone: String,
    isActive: Boolean,
    canEdit: Boolean = true,
    statusMessage: String = "",
    onSaveSettings: (phone: String, active: Boolean) -> Unit,
) {
    var phoneInput by remember(currentPhone) { mutableStateOf(currentPhone) }
    var activeState by remember(isActive) { mutableStateOf(isActive) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إعدادات بوت الواتساب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0E4D8F))
            Text("عند تغيير الرقم وحفظ الإعدادات، سيقوم البوت تلقائياً بطلب كود ربط جديد للرقم المدخل.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF526070))
            OutlinedTextField(
                colors = borgTextFieldColors(),
                value = phoneInput,
                onValueChange = { phoneInput = it.filter { char -> char.isDigit() } },
                label = { Text("رقم هاتف البوت (بدون +)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
                singleLine = true,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("تفعيل البوت", fontWeight = FontWeight.Bold, color = DeepNavy)
                Switch(checked = activeState, onCheckedChange = { activeState = it }, enabled = canEdit)
            }
            if (statusMessage.isNotBlank()) Text(statusMessage, color = if (activeState) Color(0xFF2FA66A) else Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onSaveSettings(phoneInput, activeState) }, modifier = Modifier.fillMaxWidth(), enabled = canEdit) { Text("حفظ وإرسال كود الربط") }
            if (!canEdit) Text("هذه الإعدادات للمدير فقط.", color = BorgRed, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun BotLogsReportScreen(logs: List<BotLog>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "سجل استعلامات المناديب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0E4D8F))
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text("لا توجد استعلامات مسجلة حتى الآن.", color = Color(0xFF697386)) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    logs.forEach { log ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFF)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "رقم المرسل: ${log.senderPhone}", fontWeight = FontWeight.Bold, color = DeepNavy)
                                    Text(text = log.createdAt, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = "النص المرسل: \"${log.queryText}\"", color = DeepNavy)
                                Text(text = "الشركة المطابقة: ${log.matchedCompany}", fontWeight = FontWeight.Bold, color = Color(0xFF2FA66A))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(state: BorgUiState, modifier: Modifier) {
    var from by rememberSaveable { mutableStateOf(state.cycleInfo.currentCycleStart.format(shortDateFormatter)) }
    var to by rememberSaveable { mutableStateOf(state.cycleInfo.currentCycleEnd.format(shortDateFormatter)) }
    val compliant = state.dashboardScores.filter { it.expectedVisits > 0 && it.completedVisits >= it.expectedVisits }
    val nonVisiting = state.dashboardScores.filter { it.expectedVisits > 0 && it.completedVisits == 0 }
    val noReps = state.companies.filter { state.repsByCompany[it.id].isNullOrEmpty() }
    val weak = state.dashboardScores.filter { it.expectedVisits > 0 && it.completedVisits > 0 && it.scoreOutOf10 < 5.0 }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeaderCard("لوحة التحكم والتقارير", "إحصائيات فورية وتقارير الالتزام للفترة المحددة.", listOf(DeepNavy, BorgBlue)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CountCard("إجمالي الشركات", state.companies.size.toString(), BorgRed, Modifier.weight(1f))
                CountCard("المندوبون", state.representatives.size.toString(), BorgBlue, Modifier.weight(1f))
                CountCard("زيارات الدورة", state.visits.count { it.cycleStartEpochDay == state.cycleInfo.currentCycleStart.toEpochDay() }.toString(), Color(0xFF2FA66A), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
        colors = borgTextFieldColors(),value = from, onValueChange = { from = it }, label = { Text("من") }, modifier = Modifier.weight(1f))
                OutlinedTextField(
        colors = borgTextFieldColors(),value = to, onValueChange = { to = it }, label = { Text("إلى") }, modifier = Modifier.weight(1f))
            }
        }
        item { ReportCard("1. الشركات الملتزمة", compliant.map { "${it.company.name}: ${"%.1f".format(it.scoreOutOf10)}/10" }) }
        item { ReportCard("2. الشركات غير الزائرة", nonVisiting.map { it.company.name }) }
        item { ReportCard("3. شركات بلا مندوبين مسجلين", noReps.map { it.name }) }
        item { ReportCard("4. شركات ضعيفة / قليلة الزيارة", weak.map { "${it.company.name}: ${"%.1f".format(it.scoreOutOf10)}/10" }) }
    }
}

@Composable
private fun SettingsScreen(
    state: BorgUiState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDriveBackup: () -> Unit,
    onSync: () -> Unit,
    onCreateUser: (String, String, UserRole, String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeaderCard("الإعدادات والنسخ الاحتياطي", "يتم إنشاء نسخة احتياطية تلقائيًا داخل BORG PHARMACY/BACKUP عند التشغيل وأي تعديل.", listOf(Color(0xFF526070), DeepNavy)) }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBackup) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(6.dp)); Text("نسخ احتياطي محلي") }
                    OutlinedButton(onClick = onRestore, enabled = state.isAdmin) { Text("استعادة نسخة محلية") }
                    OutlinedButton(onClick = onDriveBackup) { Text("نسخ احتياطي عبر Google Drive") }
                    OutlinedButton(onClick = onSync) { Text("مزامنة Supabase") }
                }
            }
        }
        if (state.isAdmin) {
            item { UserManagementCard(state, onCreateUser) }
        }
    }
}

@Composable
private fun UserManagementCard(state: BorgUiState, onCreateUser: (String, String, UserRole, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var passcode by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(UserRole.PHARMACIST.name) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("إدارة المستخدمين - للمدير فقط", fontWeight = FontWeight.ExtraBold, color = DeepNavy)
            state.users.forEach { user -> AssistChip(onClick = {}, label = { Text("${user.username} • ${user.role.arabicLabel()}") }) }
            OutlinedTextField(
        colors = borgTextFieldColors(),value = username, onValueChange = { username = it }, label = { Text("اسم المستخدم") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
        colors = borgTextFieldColors(),value = displayName, onValueChange = { displayName = it }, label = { Text("الاسم الظاهر") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
        colors = borgTextFieldColors(),value = passcode, onValueChange = { passcode = it }, label = { Text("كلمة المرور") }, modifier = Modifier.fillMaxWidth())
            RoleDropdown(UserRole.valueOf(role)) { role = it.name }
            Button(onClick = { onCreateUser(username, displayName, UserRole.valueOf(role), passcode); username = ""; displayName = ""; passcode = "" }) {
                Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("إنشاء مستخدم")
            }
        }
    }
}

@Composable
private fun RoleDropdown(selected: UserRole, onSelected: (UserRole) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected.arabicLabel()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UserRole.entries.forEach { role -> DropdownMenuItem(text = { Text(role.arabicLabel()) }, onClick = { onSelected(role); expanded = false }) }
        }
    }
}

@Composable
private fun HeaderCard(title: String, subtitle: String, gradient: List<Color>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient), RoundedCornerShape(26.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
        }
    }
}

@Composable
private fun ExportDropdown(label: String, onExport: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.Default.UploadFile, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("PDF") }, onClick = { expanded = false; onExport("pdf") })
            DropdownMenuItem(text = { Text("CSV") }, onClick = { expanded = false; onExport("csv") })
            DropdownMenuItem(text = { Text("HTML") }, onClick = { expanded = false; onExport("html") })
        }
    }
}

private fun List<Visit>.displaySorted(): List<Visit> = sortedWith(compareBy<Visit> { it.createdAt }.thenBy { it.id })

@Composable
private fun borgTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DeepNavy,
    unfocusedTextColor = DeepNavy,
    disabledTextColor = DeepNavy.copy(alpha = 0.55f),
    cursorColor = BorgBlue,
    focusedBorderColor = BorgBlue,
    unfocusedBorderColor = Color(0xFFB8C5D6),
    focusedLabelColor = BorgBlue,
    unfocusedLabelColor = DeepNavy,
)

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        colors = borgTextFieldColors(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CountCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelLarge, color = DeepNavy)
        }
    }
}

@Composable
private fun ReportCard(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = DeepNavy)
            if (lines.isEmpty()) Text("لا توجد سجلات.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = DeepNavy) }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF3F6FA))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF697386), textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatusBadge(status: VisitStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        VisitStatus.SCHEDULED -> BorgBlue
        VisitStatus.COMPLETED -> Color(0xFF2FA66A)
        VisitStatus.MISSED -> BorgRed
        VisitStatus.CANCELLED -> Color(0xFF697386)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            status.arabicLabel(),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.10f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

private fun Tier.arabicLabel(): String = when (this) {
    Tier.A -> "الفئة A"
    Tier.B -> "الفئة B"
    Tier.C -> "الفئة C"
    Tier.UNRATED -> "غير مصنفة"
}

private fun Tier.color(): Color = when (this) {
    Tier.A -> BorgRed
    Tier.B -> BorgBlue
    Tier.C -> Color(0xFF2FA66A)
    Tier.UNRATED -> Color(0xFF697386)
}

private fun VisitStatus.arabicLabel(): String = when (this) {
    VisitStatus.SCHEDULED -> "مجدولة"
    VisitStatus.COMPLETED -> "تمت"
    VisitStatus.MISSED -> "لم يحضر"
    VisitStatus.CANCELLED -> "ملغاة"
}

private fun UserRole.arabicLabel(): String = when (this) {
    UserRole.ADMIN -> "مدير"
    UserRole.PHARMACIST -> "صيدلي"
}
