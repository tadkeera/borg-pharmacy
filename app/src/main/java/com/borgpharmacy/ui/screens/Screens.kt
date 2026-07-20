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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

private val BorgBlue = Color(0xFF0E4D8F)
private val BorgRed = Color(0xFFC8172B)
private val DeepNavy = Color(0xFF082B52)
private val SoftBlue = Color(0xFFEAF4FF)
private val SoftRed = Color(0xFFFFEEF1)

private enum class Route(val label: String, val icon: ImageVector) {
    HOME("اليوم", Icons.Default.Home),
    WEEKLY("الأسابيع", Icons.Default.CalendarMonth),
    COMPANIES("الشركات", Icons.Default.Business),
    ENQUIRIES("التواصل", Icons.Default.Send),
    BOT("البوت", Icons.Default.Sync),
    DASHBOARD("التقارير", Icons.Default.Assessment),
    SETTINGS("الإعدادات", Icons.Default.Settings),
    MORE("المزيد", Icons.Default.MoreHoriz),
}


@Composable
private fun BorgColoredIcon(route: Route, isSelected: Boolean) {
    val size = 26.dp
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        when (route) {
            Route.HOME -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(BorderStroke(1.2.dp, Color(0xFFD1D5DB)), RoundedCornerShape(6.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(7.dp).background(Color(0xFFE11D48)))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("17", color = DeepNavy, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                }
            }
            Route.WEEKLY -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(BorderStroke(1.2.dp, Color(0xFF2563EB)), RoundedCornerShape(6.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(7.dp).background(Color(0xFF2563EB)))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("4\nWk", color = DeepNavy, fontSize = 7.sp, lineHeight = 7.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                }
            }
            Route.COMPANIES -> {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(
                        modifier = Modifier
                            .size(20.dp, 24.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(Color(0xFF94A3B8))
                            .border(BorderStroke(1.dp, Color(0xFF64748B)), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.size(3.dp).background(Color.White)); Box(modifier = Modifier.size(3.dp).background(Color.White))
                            }
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.size(3.dp).background(Color.White)); Box(modifier = Modifier.size(3.dp).background(Color.White))
                            }
                            Box(modifier = Modifier.size(4.dp, 6.dp).background(Color(0xFF3B82F6)))
                        }
                    }
                }
            }
            Route.ENQUIRIES -> {
                Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF128C7E)), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color.White)); Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color.White)); Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
            Route.BOT -> {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF64748B)), contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.Yellow)); Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.Yellow))
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                    Box(modifier = Modifier.size(8.dp, 2.dp).background(Color.Red))
                }
            }
            Route.MORE -> {
                Row(modifier = Modifier.size(24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF0E4D8F)))
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF64748B)))
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFC8172B)))
                }
            }
            Route.DASHBOARD -> {
                Row(modifier = Modifier.size(22.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    Box(modifier = Modifier.size(4.dp, 10.dp).background(Color(0xFF22C55E)))
                    Box(modifier = Modifier.size(4.dp, 18.dp).background(Color(0xFFEF4444)))
                    Box(modifier = Modifier.size(4.dp, 14.dp).background(Color(0xFF3B82F6)))
                }
            }
            Route.SETTINGS -> {
                Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF0E4D8F)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
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
    onShareToday: () -> Unit,
    onPrint: (Company, Representative, Visit) -> Unit,
    onWhatsApp: (Company, Representative) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDriveBackup: () -> Unit,
    onSync: () -> Unit,
    onSaveBotSettings: (String, Boolean) -> Unit,
    onRefreshBotData: () -> Unit,
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BorgBlue) }
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
        var showMoreSheet by rememberSaveable { mutableStateOf(false) }
        val selected = Route.valueOf(route)
        val mainRoutes = listOf(Route.HOME, Route.WEEKLY, Route.COMPANIES, Route.DASHBOARD, Route.MORE)
        if (showMoreSheet) {
            MoreRoutesBottomSheet(
                onDismiss = { showMoreSheet = false },
                onRouteSelected = { target ->
                    route = target.name
                    showMoreSheet = false
                }
            )
        }
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = { BorgTopBar(state = state, onLogout = onLogout) },
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    mainRoutes.forEach { item ->
                        val itemSelected = selected == item || (item == Route.MORE && selected in listOf(Route.BOT, Route.ENQUIRIES, Route.SETTINGS))
                        NavigationBarItem(
                            selected = itemSelected,
                            onClick = { if (item == Route.MORE) showMoreSheet = true else route = item.name },
                            icon = { BorgColoredIcon(item, itemSelected) },
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BorgBlue,
                                selectedTextColor = BorgBlue,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = SoftBlue
                            )
                        )
                    }
                }
            },
        ) { padding ->
            val contentModifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF4F7FC))
            when (selected) {
                Route.HOME -> HomeScreen(state, onPrint, onMarkVisitStatus, onShareToday, onSync, contentModifier)
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
                Route.BOT -> WhatsAppBotScreen(state = state, onSaveBotSettings = onSaveBotSettings, onRefreshBotData = onRefreshBotData, modifier = contentModifier)
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
                Route.MORE -> Unit
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
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(painter = painterResource(R.drawable.borg_logo), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "صيدلية برج الأطباء",
                    fontWeight = FontWeight.ExtraBold,
                    color = DeepNavy,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 20.sp,
                    maxLines = 1,
                )
            }
        },
        actions = {
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "تسجيل الخروج", tint = BorgRed) }
        },
    )
}

@Composable
private fun MoreRoutesBottomSheet(onDismiss: () -> Unit, onRouteSelected: (Route) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المزيد", color = DeepNavy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 6.dp))
            listOf(Route.BOT, Route.ENQUIRIES, Route.SETTINGS).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF4F8FC))
                        .clickable { onRouteSelected(item) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) { BorgColoredIcon(item, true) }
                    Text(item.label, color = DeepNavy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
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
                        colors = borgTextFieldColors(),
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("اسم المستخدم") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        colors = borgTextFieldColors(),
                        value = passcode,
                        onValueChange = { passcode = it },
                        label = { Text("كود التفعيل / كلمة المرور") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onLogin(username, passcode) },
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgBlue),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("دخول", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text("الكود الافتراضي للمدير: admin2026 ويجب تغييره فورًا عند أول دخول", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = Color.Gray)
                } else {
                    Text("لأمان النظام يجب تغيير كود المدير الافتراضي الآن.", fontWeight = FontWeight.SemiBold, color = BorgRed)
                    OutlinedTextField(
                        colors = borgTextFieldColors(),
                        value = newPasscode,
                        onValueChange = { newPasscode = it },
                        label = { Text("الكود الجديد") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onChangeForcedPasscode(newPasscode) },
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgBlue),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("حفظ الكود الجديد", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                state.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    gradient: List<Color> = listOf(DeepNavy, BorgBlue)
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Brush.horizontalGradient(gradient))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomeScreen(
    state: BorgUiState,
    onPrint: (Company, Representative, Visit) -> Unit,
    onMarkVisitStatus: (String, VisitStatus) -> Unit,
    onShareToday: () -> Unit,
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
                onShare = onShareToday,
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
    onShare: () -> Unit,
    onSync: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))), RoundedCornerShape(24.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                date.format(arabicLongDateFormatter),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
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
                    OutlinedButton(onClick = onShare) { Text("مشاركة", color = Color.White) }
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(softAccent), contentAlignment = Alignment.Center) {
                    Text(if (title.contains("الصباحية")) "☀️" else "🌙", fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = DeepNavy)
                    Text("${visits.size} شركة", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    company.name,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = DeepNavy,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(50)).background(accent))
            }

            if (expanded) {
                HorizontalDivider(color = accent.copy(alpha = 0.1f))
                if (reps.isEmpty()) {
                    Text("لا يوجد مندوبون مسجلون لهذه الشركة.", color = Color.Gray, fontSize = 12.sp)
                }
                reps.forEach { rep ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(rep.name, fontWeight = FontWeight.Bold, color = DeepNavy, fontSize = 13.sp)
                            Text(rep.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Text("تمت الطباعة: ${state.printCountMap[rep.id to visit.id] ?: 0}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { onPrint(company, rep, visit) }) { Icon(Icons.Default.Print, contentDescription = "طباعة تصريح", tint = accent) }
                    }
                }
                if (state.isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onMarkVisitStatus(visit.id, VisitStatus.COMPLETED) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorgBlue)
                        ) { Text("تأكيد الزيارة", fontSize = 12.sp, color = BorgBlue, fontWeight = FontWeight.Bold) }
                        
                        OutlinedButton(
                            onClick = { onMarkVisitStatus(visit.id, VisitStatus.MISSED) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorgRed)
                        ) { Text("لم يحضر", fontSize = 12.sp, color = BorgRed, fontWeight = FontWeight.Bold) }
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
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "جداول الزيارات الأسبوعية",
            gradient = listOf(DeepNavy, BorgBlue)
        )
        
        Spacer(Modifier.height(12.dp))
        
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE2EBF5))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (1..4).forEach { week ->
                    val active = selectedWeek == week
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (active) Color.White else Color.Transparent)
                            .clickable { selectedWeek = week }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "الأسبوع $week",
                            color = if (active) DeepNavy else Color(0xFF5A728E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                ExportDropdown(label = "تصدير", onExport = onExportSchedules)
            }
        }
        
        Spacer(Modifier.height(14.dp))
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(BorderStroke(1.dp, Color(0xFFE2ECF5)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    day.format(arabicLongDateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DeepNavy
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SoftBlue)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "${visits.size} زيارة",
                    color = BorgBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        HorizontalDivider(color = Color(0xFFF0F4F8), thickness = 1.dp)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeeklyShiftPanel(
                title = "المساء",
                visits = evening,
                companies = companies,
                accent = BorgRed,
                softAccent = SoftRed,
                modifier = Modifier.weight(1f)
            )
            
            WeeklyShiftPanel(
                title = "الصباح",
                visits = morning,
                companies = companies,
                accent = BorgBlue,
                softAccent = SoftBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeeklyShiftPanel(
    title: String,
    visits: List<Visit>,
    companies: Map<String, Company>,
    accent: Color,
    softAccent: Color,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            color = accent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        if (visits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(softAccent.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد زيارات", fontSize = 11.sp, color = Color(0xFF8A99AD))
            }
        } else {
            visits.forEachIndexed { index, visit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(BorderStroke(1.dp, Color(0xFFE2ECF5)), RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(accent)
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    Text(
                        "${index + 1}",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.width(14.dp),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.width(4.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(softAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(6.dp))
                    
                    Text(
                        companies[visit.companyId]?.name ?: "شركة غير معروفة",
                        color = DeepNavy,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
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
            text = { Text("سيتم حذف جميع الشركات والمندوبين وجميع الزيارات المرتبطة بها. هل تريد المتابعة؟") },
            confirmButton = {
                Button(onClick = { showDeleteAllConfirm = false; onDeleteAllCompanies() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgRed)) { Text("نعم، حذف الكل", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllConfirm = false }) { Text("تراجع") }
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "الشركات",
            gradient = listOf(DeepNavy, BorgBlue)
        )
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchBox(query, { query = it }, "ابحث عن شركة")
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            colors = borgTextFieldColors(),
                            value = newCompany,
                            onValueChange = { newCompany = it },
                            placeholder = { Text("اسم شركة جديدة", color = Color.Gray) },
                            enabled = state.isAdmin,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onAddCompany(newCompany); newCompany = "" },
                            enabled = state.isAdmin && newCompany.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgBlue),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("إضافة", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionButton(
                            label = "استيراد CSV",
                            icon = Icons.Default.UploadFile,
                            onClick = onImportCsv,
                            backgroundColor = Color(0xFFF1F5F9),
                            contentColor = DeepNavy,
                            enabled = state.isAdmin,
                            modifier = Modifier.weight(1f)
                        )
                        ExportDropdown(label = "تصدير", onExport = onExportCompanies)
                        ActionButton(
                            label = "حذف الكل",
                            icon = Icons.Default.Delete,
                            onClick = { showDeleteAllConfirm = true },
                            backgroundColor = SoftRed,
                            contentColor = BorgRed,
                            enabled = state.isAdmin && state.companies.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    if (query.length in 1..2) {
                        Text("اكتب 3 أحرف لعرض الاقتراحات الذكية.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
            
            items(filtered, key = { it.id }) { company ->
                CompanyProfileCard(
                    company = company,
                    state = state,
                    onUpdateCompanyName = onUpdateCompanyName,
                    onDeleteCompany = onDeleteCompany,
                    onAddRepresentative = onAddRepresentative,
                    onDeleteRepresentative = onDeleteRepresentative
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                Button(onClick = { showDeleteConfirm = false; onDeleteCompany(company.id) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgRed)) { Text("نعم، حذف", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("تراجع") }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2ECF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showEditNamePopup) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftBlue),
                    border = BorderStroke(1.dp, BorgBlue.copy(alpha = 0.25f)),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعديل اسم الشركة", color = DeepNavy, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            colors = borgTextFieldColors(),
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("الاسم الجديد") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onUpdateCompanyName(company.id, editedName); showEditNamePopup = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("حفظ التعديلات", color = Color.White) }
                            OutlinedButton(
                                onClick = { editedName = company.name; showEditNamePopup = false },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("تراجع") }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        company.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DeepNavy
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "المعرف ثابت: ${company.id.take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        company.tier.arabicLabel(),
                        fontSize = 12.sp,
                        color = company.tier.color(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF0F4F8))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = BorgBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "المندوبون",
                            color = BorgBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = state.isAdmin) {
                                editedName = company.name
                                showEditNamePopup = true
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_edit),
                            contentDescription = null,
                            tint = BorgBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "تعديل",
                            color = BorgBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = state.isAdmin,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = BorgRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                val reps = state.repsByCompany[company.id].orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF7FAFE))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "قائمة المندوبين (${reps.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DeepNavy
                        )
                        if (state.isAdmin) {
                            TextButton(onClick = { showRepPopup = true }) {
                                Text("+ إضافة جديد", color = BorgBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (showRepPopup) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .border(BorderStroke(1.dp, Color(0xFFE2ECF5)), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            OutlinedTextField(
                                colors = borgTextFieldColors(),
                                value = repName,
                                onValueChange = { repName = it },
                                label = { Text("اسم المندوب") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                colors = borgTextFieldColors(),
                                value = repPhone,
                                onValueChange = { repPhone = it },
                                label = { Text("رقم المندوب") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onAddRepresentative(company.id, repName, repPhone)
                                        repName = ""
                                        repPhone = "+967"
                                        showRepPopup = false
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("حفظ", color = Color.White) }
                                OutlinedButton(
                                    onClick = { showRepPopup = false },
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("إلغاء") }
                            }
                        }
                    }

                    if (reps.isEmpty()) {
                        Text("لا يوجد مندوبون مسجلون.", fontSize = 12.sp, color = Color.Gray)
                    }

                    reps.forEach { rep ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rep.name, fontWeight = FontWeight.Bold, color = DeepNavy, fontSize = 13.sp)
                                Text(rep.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 11.sp)
                            }
                            if (state.isAdmin) {
                                IconButton(
                                    onClick = { onDeleteRepresentative(rep.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = BorgRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
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
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "الاستعلامات والتواصل",
            gradient = listOf(Color(0xFF0F5A47), Color(0xFF128C7E))
        )
        
        Spacer(Modifier.height(12.dp))
        
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SearchBox(query, { query = it }, "ابحث عن شركة للتواصل")
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (filtered.isEmpty()) {
                    item { EmptyState("لا توجد نتائج مطابقة لبحثك.") }
                }
                
                items(filtered, key = { it.id }) { company ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2ECF5)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE6F4F1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF128C7E))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    company.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = DeepNavy,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            HorizontalDivider(color = Color(0xFFF0F4F8))
                            
                            val reps = state.repsByCompany[company.id].orEmpty()
                            if (reps.isEmpty()) {
                                Text("لا يوجد مندوبون مسجلون.", fontSize = 12.sp, color = Color.Gray)
                            }
                            
                            reps.forEach { rep ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF8FBFF))
                                        .padding(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rep.name, fontWeight = FontWeight.Bold, color = DeepNavy, fontSize = 14.sp)
                                        Spacer(Modifier.height(2.dp))
                                        Text(rep.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 12.sp)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF25D366))
                                            .clickable { onWhatsApp(company, rep) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Send,
                                            contentDescription = "واتساب",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
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
private fun WhatsAppBotScreen(
    state: BorgUiState,
    onSaveBotSettings: (String, Boolean) -> Unit,
    onRefreshBotData: () -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) { onRefreshBotData() }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "إعدادات بوت الواتساب",
            gradient = listOf(Color(0xFF0F5A47), Color(0xFF128C7E))
        )
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                BotSettingsScreen(
                    currentPhone = state.botPhone,
                    isActive = state.botActive,
                    canEdit = state.isAdmin,
                    statusMessage = state.botStatusMessage,
                    onSaveSettings = onSaveBotSettings,
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "سجل استعلامات المندوبين",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepNavy
                    )
                    IconButton(onClick = onRefreshBotData) {
                        Icon(Icons.Default.Sync, contentDescription = "تحديث", tint = BorgBlue)
                    }
                }
            }
            
            if (state.botLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color(0xFFE2ECF5)), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا توجد استعلامات مسجلة حتى الآن.", color = Color.Gray)
                    }
                }
            } else {
                items(state.botLogs) { log ->
                    BotLogCard(log)
                }
            }
        }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2ECF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "بيانات البوت",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = DeepNavy
            )
            Text(
                "عند تغيير الرقم وحفظ الإعدادات، سيقوم البوت تلقائياً بطلب كود ربط جديد للرقم المدخل.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            OutlinedTextField(
                colors = borgTextFieldColors(),
                value = phoneInput,
                onValueChange = { phoneInput = it.filter { char -> char.isDigit() } },
                label = { Text("رقم هاتف البوت (بدون +)") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
                singleLine = true,
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تفعيل البوت", fontWeight = FontWeight.Bold, color = DeepNavy)
                Switch(
                    checked = activeState,
                    onCheckedChange = { activeState = it },
                    enabled = canEdit,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF128C7E)
                    )
                )
            }
            
            if (statusMessage.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activeState) Color(0xFFEBF7EE) else Color(0xFFF1F5F9))
                        .padding(10.dp)
                ) {
                    Text(
                        statusMessage,
                        color = if (activeState) Color(0xFF1E6B3F) else Color(0xFF4A5568),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Button(
                onClick = { onSaveSettings(phoneInput, activeState) },
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = canEdit
            ) {
                Text("حفظ وإرسال كود الربط", fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            if (!canEdit) {
                Text("هذه الإعدادات للمدير فقط.", color = BorgRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun BotLogCard(log: BotLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2ECF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "رقم المرسل: ${log.senderPhone}",
                        fontWeight = FontWeight.Bold,
                        color = DeepNavy,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = log.createdAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            HorizontalDivider(color = Color(0xFFF0F4F8))
            Text(
                text = "النص المرسل: \"${log.queryText}\"",
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE6F4F1))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "الشركة المطابقة: ${log.matchedCompany}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF128C7E),
                    fontSize = 12.sp
                )
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
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "الإعدادات والنسخ الاحتياطي",
            gradient = listOf(Color(0xFF4A5568), Color(0xFF1A202C))
        )
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2ECF5)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "النسخ الاحتياطي والمزامنة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepNavy
                        )
                        Text(
                            "يتم إنشاء نسخة احتياطية تلقائيًا داخل BORG PHARMACY/BACKUP عند التشغيل وأي تعديل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        
                        HorizontalDivider(color = Color(0xFFF0F4F8))
                        
                        Button(
                            onClick = onBackup,
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Backup, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("نسخ احتياطي محلي الآن", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        
                        OutlinedButton(
                            onClick = onRestore,
                            enabled = state.isAdmin,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("استعادة نسخة محلية", fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = onDriveBackup,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("نسخ احتياطي عبر Google Drive", fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = onSync,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, null)
                            Spacer(Modifier.width(8.dp))
                            Text("مزامنة Supabase الفورية", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            if (state.isAdmin) {
                item {
                    UserManagementCard(state, onCreateUser)
                }
            }
        }
    }
}

@Composable
private fun UserManagementCard(state: BorgUiState, onCreateUser: (String, String, UserRole, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var passcode by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(UserRole.PHARMACIST.name) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2ECF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "إدارة مستخدمي النظام (للمدير فقط)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DeepNavy
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.users.forEach { user ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFF0F4F8))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${user.username} (${user.role.arabicLabel()})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepNavy
                        )
                    }
                }
            }
            
            HorizontalDivider(color = Color(0xFFF0F4F8))
            
            OutlinedTextField(
                colors = borgTextFieldColors(),
                value = username,
                onValueChange = { username = it },
                label = { Text("اسم المستخدم") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                colors = borgTextFieldColors(),
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("الاسم الظاهر") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                colors = borgTextFieldColors(),
                value = passcode,
                onValueChange = { passcode = it },
                label = { Text("كلمة المرور") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("نوع الصلاحية:", fontWeight = FontWeight.Bold, color = DeepNavy)
                RoleDropdown(UserRole.valueOf(role)) { role = it.name }
            }
            
            Button(
                onClick = { onCreateUser(username, displayName, UserRole.valueOf(role), passcode); username = ""; displayName = ""; passcode = "" },
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BorgBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonAdd, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("إنشاء مستخدم جديد", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun RoleDropdown(selected: UserRole, onSelected: (UserRole) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(10.dp)) { Text(selected.arabicLabel()) }
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
        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    unfocusedBorderColor = Color(0xFFE2ECF5),
    focusedLabelColor = BorgBlue,
    unfocusedLabelColor = DeepNavy,
)

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        colors = borgTextFieldColors(),
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label, color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = BorgBlue) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
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
            if (lines.isEmpty()) Text("لا توجد سجلات.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
            .background(Color(0xFFEAF2F8))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DeepNavy, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

private fun Tier.arabicLabel(): String = when (this) {
    Tier.A -> "الفئة A (ثلاث زيارات)"
    Tier.B -> "الفئة B (زيارتان)"
    Tier.C -> "الفئة C (زيارة واحدة)"
    Tier.UNRATED -> "غير مصنفة"
}

private fun Tier.color(): Color = when (this) {
    Tier.A -> BorgRed
    Tier.B -> BorgBlue
    Tier.C -> Color(0xFF2FA66A)
    Tier.UNRATED -> Color.Gray
}

private fun UserRole.arabicLabel(): String = when (this) {
    UserRole.ADMIN -> "مدير"
    UserRole.PHARMACIST -> "صيدلي"
}