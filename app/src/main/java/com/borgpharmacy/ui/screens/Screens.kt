package com.borgpharmacy.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private enum class Route(val label: String, val icon: ImageVector) {
    HOME("Daily", Icons.Default.Home),
    WEEKLY("Weekly", Icons.Default.CalendarMonth),
    COMPANIES("Companies", Icons.Default.Business),
    EVALUATION("Tiering", Icons.Default.Edit),
    ENQUIRIES("Enquiries", Icons.Default.Send),
    DASHBOARD("Reports", Icons.Default.Assessment),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun BorgApp(
    state: BorgUiState,
    onLogin: (String, String) -> Unit,
    onChangeForcedPasscode: (String) -> Unit,
    onLogout: () -> Unit,
    onAddCompany: (String) -> Unit,
    onImportCsv: () -> Unit,
    onUpdateTier: (String, Tier) -> Unit,
    onSaveEvaluations: () -> Unit,
    onDeleteCompany: (String) -> Unit,
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissMessage()
    }

    if (!state.initialized) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (!state.isUnlocked) {
        LockScreen(
            state = state,
            onLogin = onLogin,
            onChangeForcedPasscode = onChangeForcedPasscode,
        )
        return
    }

    var route by rememberSaveable { mutableStateOf(Route.HOME.name) }
    val selected = Route.valueOf(route)
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            BorgTopBar(state = state, onLogout = onLogout)
        },
        bottomBar = {
            NavigationBar {
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
        when (selected) {
            Route.HOME -> HomeScreen(state, onPrint, onMarkVisitStatus, contentModifier)
            Route.WEEKLY -> WeeklyScreen(state, contentModifier)
            Route.COMPANIES -> CompanyProfilesScreen(
                state = state,
                onAddCompany = onAddCompany,
                onImportCsv = onImportCsv,
                onDeleteCompany = onDeleteCompany,
                onAddRepresentative = onAddRepresentative,
                onDeleteRepresentative = onDeleteRepresentative,
                modifier = contentModifier,
            )
            Route.EVALUATION -> EvaluationScreen(state, onImportCsv, onUpdateTier, onSaveEvaluations, contentModifier)
            Route.ENQUIRIES -> EnquiriesScreen(state, onWhatsApp, contentModifier)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BorgTopBar(state: BorgUiState, onLogout: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.borg_logo), contentDescription = null, modifier = Modifier.size(42.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(state.cycleInfo.title, fontWeight = FontWeight.Bold)
                    Text(
                        "Actual date: ${state.cycleInfo.today.format(dateFormatter)} • Day ${state.cycleInfo.dayOfCycle}/28",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        actions = {
            Text(state.currentUser?.role?.name.orEmpty(), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "Logout") }
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
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = painterResource(R.drawable.borg_logo), contentDescription = null, modifier = Modifier.size(120.dp))
                Text("Borg Pharmacy Activation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (state.mustChangePasscodeUser == null) {
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(passcode, { passcode = it }, label = { Text("Activation code / passcode") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onLogin(username, passcode) }, modifier = Modifier.fillMaxWidth()) { Text("Login") }
                    Text("Default master admin code: admin2026 (must be changed immediately)", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("First login requires changing the default admin code.", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(newPasscode, { newPasscode = it }, label = { Text("New admin code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onChangeForcedPasscode(newPasscode) }, modifier = Modifier.fillMaxWidth()) { Text("Save New Code") }
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
    modifier: Modifier,
) {
    val today = state.cycleInfo.today
    val companies = state.companies.associateBy { it.id }
    val visitsToday = state.visits.filter { it.date == today && companies.containsKey(it.companyId) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HeaderCard(
                title = "Daily Visits Schedule",
                subtitle = "${today.dayOfWeek.borgArabicName()} • ${today.format(dateFormatter)} • Week ${state.cycleInfo.weekOfCycle}",
            )
        }
        item {
            ShiftTable(
                title = "Morning Shift",
                visits = visitsToday.filter { it.shift == Shift.MORNING }.sortedBy { it.slotIndex },
                companies = companies,
                state = state,
                onPrint = onPrint,
                onMarkVisitStatus = onMarkVisitStatus,
            )
        }
        item {
            ShiftTable(
                title = "Evening Shift",
                visits = visitsToday.filter { it.shift == Shift.EVENING }.sortedBy { it.slotIndex },
                companies = companies,
                state = state,
                onPrint = onPrint,
                onMarkVisitStatus = onMarkVisitStatus,
            )
        }
    }
}

@Composable
private fun ShiftTable(
    title: String,
    visits: List<Visit>,
    companies: Map<String, Company>,
    state: BorgUiState,
    onPrint: (Company, Representative, Visit) -> Unit,
    onMarkVisitStatus: (String, VisitStatus) -> Unit,
) {
    var expandedVisitId by rememberSaveable { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (visits.isEmpty()) Text("No companies scheduled in this shift.")
            visits.forEach { visit ->
                val company = companies[visit.companyId] ?: return@forEach
                val expanded = expandedVisitId == visit.id
                Card(
                    Modifier.fillMaxWidth().clickable { expandedVisitId = if (expanded) null else visit.id },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${visit.slotIndex}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(company.name, fontWeight = FontWeight.Bold)
                                Text("${company.tier.label} • ${visit.status}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.isAdmin) {
                                TextButton(onClick = { onMarkVisitStatus(visit.id, VisitStatus.COMPLETED) }) { Text("Complete") }
                            }
                        }
                        if (expanded) {
                            Divider(Modifier.padding(vertical = 8.dp))
                            val reps = state.repsByCompany[company.id].orEmpty()
                            if (reps.isEmpty()) Text("No registered representatives.")
                            reps.forEach { rep ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text(rep.name)
                                        Text(rep.phone, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Printed: ${state.printCountMap[rep.id to visit.id] ?: 0}", style = MaterialTheme.typography.labelMedium)
                                    IconButton(onClick = { onPrint(company, rep, visit) }) { Icon(Icons.Default.Print, contentDescription = "Print") }
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
private fun WeeklyScreen(state: BorgUiState, modifier: Modifier) {
    var selectedWeek by rememberSaveable { mutableStateOf(1) }
    val companies = state.companies.associateBy { it.id }
    Column(modifier.padding(16.dp)) {
        Text("Weekly Schedules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = selectedWeek - 1) {
            (1..4).forEach { week -> Tab(selected = selectedWeek == week, onClick = { selectedWeek = week }, text = { Text("Week $week") }) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val weekStart = state.cycleInfo.currentCycleStart.plusDays(((selectedWeek - 1) * 7).toLong())
            val days = (0..6).map { weekStart.plusDays(it.toLong()) }.filter { it.dayOfWeek.isBorgWorkingDay() }
            items(days) { day ->
                val visits = state.visits.filter { it.date == day }
                WeeklyDayCard(day, visits, companies)
            }
        }
    }
}

@Composable
private fun WeeklyDayCard(day: LocalDate, visits: List<Visit>, companies: Map<String, Company>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${day.dayOfWeek.borgArabicName()} • ${day.format(dateFormatter)}", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeeklyShiftColumn("Morning", visits.filter { it.shift == Shift.MORNING }, companies, Modifier.weight(1f))
                WeeklyShiftColumn("Evening", visits.filter { it.shift == Shift.EVENING }, companies, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeeklyShiftColumn(title: String, visits: List<Visit>, companies: Map<String, Company>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        if (visits.isEmpty()) Text("—", style = MaterialTheme.typography.bodySmall)
        visits.sortedBy { it.slotIndex }.forEach { visit ->
            Text("${visit.slotIndex}. ${companies[visit.companyId]?.name ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CompanyProfilesScreen(
    state: BorgUiState,
    onAddCompany: (String) -> Unit,
    onImportCsv: () -> Unit,
    onDeleteCompany: (String) -> Unit,
    onAddRepresentative: (String, String, String) -> Unit,
    onDeleteRepresentative: (String) -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var newCompany by rememberSaveable { mutableStateOf("") }
    val filtered = state.companies.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderCard("Company Profiles", "Smart search starts after 3 characters; CSV import supported.") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBox(query, { query = it }, "Search companies")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newCompany, { newCompany = it }, label = { Text("New company") }, enabled = state.isAdmin, modifier = Modifier.weight(1f))
                    Button(onClick = { onAddCompany(newCompany); newCompany = "" }, enabled = state.isAdmin) { Text("Add") }
                }
                OutlinedButton(onClick = onImportCsv, enabled = state.isAdmin) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("Import CSV") }
                if (query.length in 1..2) Text("Type 3 characters for smart dropdown suggestions.", style = MaterialTheme.typography.bodySmall)
                if (query.length >= 3) Text("${filtered.size} suggestion(s) found", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(filtered, key = { it.id }) { company ->
            CompanyProfileCard(company, state, onDeleteCompany, onAddRepresentative, onDeleteRepresentative)
        }
    }
}

@Composable
private fun CompanyProfileCard(
    company: Company,
    state: BorgUiState,
    onDeleteCompany: (String) -> Unit,
    onAddRepresentative: (String, String, String) -> Unit,
    onDeleteRepresentative: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var repName by rememberSaveable { mutableStateOf("") }
    var repPhone by rememberSaveable { mutableStateOf("+967") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(company.name, fontWeight = FontWeight.Bold)
                    Text("ID: ${company.id.take(8)} • ${company.tier.label}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide reps" else "Reps") }
                IconButton(onClick = { onDeleteCompany(company.id) }, enabled = state.isAdmin) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
            if (expanded) {
                val reps = state.repsByCompany[company.id].orEmpty()
                reps.forEach { rep ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${rep.name} • ${rep.phone}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteRepresentative(rep.id) }, enabled = state.isAdmin) { Icon(Icons.Default.Delete, null) }
                    }
                }
                Divider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(repName, { repName = it }, label = { Text("Rep Name") }, enabled = state.isAdmin, modifier = Modifier.weight(1f))
                    OutlinedTextField(repPhone, { repPhone = it }, label = { Text("Phone") }, enabled = state.isAdmin, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onAddRepresentative(company.id, repName, repPhone); repName = ""; repPhone = "+967" }, enabled = state.isAdmin) { Icon(Icons.Default.Save, null) }
                }
            }
        }
    }
}

@Composable
private fun EvaluationScreen(
    state: BorgUiState,
    onImportCsv: () -> Unit,
    onUpdateTier: (String, Tier) -> Unit,
    onSaveEvaluations: () -> Unit,
    modifier: Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = state.companies.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeaderCard("Company Evaluation & Tiering", "Tier A=3 visits, B=2 visits, C=1 visit per fixed 28-day cycle.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveEvaluations, enabled = state.isAdmin) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Save Evaluations") }
                OutlinedButton(onClick = onImportCsv, enabled = state.isAdmin) { Text("Import CSV") }
            }
            Spacer(Modifier.height(8.dp))
            SearchBox(query, { query = it }, "Search for evaluation")
        }
        items(filtered, key = { it.id }) { company ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(company.name, fontWeight = FontWeight.Bold)
                        Text("Expected visits: ${company.tier.visitsPerCycle}", style = MaterialTheme.typography.bodySmall)
                    }
                    TierDropdown(company.tier, enabled = state.isAdmin) { tier -> onUpdateTier(company.id, tier) }
                }
            }
        }
    }
}

@Composable
private fun TierDropdown(selected: Tier, enabled: Boolean, onSelected: (Tier) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(selected.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(Tier.A, Tier.B, Tier.C, Tier.UNRATED).forEach { tier ->
                DropdownMenuItem(text = { Text(tier.label) }, onClick = { onSelected(tier); expanded = false })
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
        item { HeaderCard("Enquiries & Communications", "Open WhatsApp with the full itinerary message for each representative.") }
        item { SearchBox(query, { query = it }, "Search company") }
        items(filtered, key = { it.id }) { company ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(company.name, fontWeight = FontWeight.Bold)
                    val reps = state.repsByCompany[company.id].orEmpty()
                    if (reps.isEmpty()) Text("No registered representatives.")
                    reps.forEach { rep ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${rep.name} • ${rep.phone}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { onWhatsApp(company, rep) }) { Icon(Icons.Default.Send, contentDescription = "WhatsApp") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(state: BorgUiState, modifier: Modifier) {
    var from by rememberSaveable { mutableStateOf(state.cycleInfo.currentCycleStart.format(dateFormatter)) }
    var to by rememberSaveable { mutableStateOf(state.cycleInfo.currentCycleEnd.format(dateFormatter)) }
    val compliant = state.dashboardScores.filter { it.expectedVisits > 0 && it.completedVisits >= it.expectedVisits }
    val nonVisiting = state.dashboardScores.filter { it.expectedVisits > 0 && it.completedVisits == 0 }
    val noReps = state.companies.filter { state.repsByCompany[it.id].isNullOrEmpty() }
    val weak = state.dashboardScores.filter { it.expectedVisits > 0 && it.completedVisits > 0 && it.scoreOutOf10 < 5.0 }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeaderCard("Dashboard & Reports", "Statistics and compliance reports for the active cycle.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CountCard("Tier A", state.companies.count { it.tier == Tier.A }.toString(), Modifier.weight(1f))
                CountCard("Tier B", state.companies.count { it.tier == Tier.B }.toString(), Modifier.weight(1f))
                CountCard("Tier C", state.companies.count { it.tier == Tier.C }.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("From") }, modifier = Modifier.weight(1f))
                OutlinedTextField(to, { to = it }, label = { Text("To") }, modifier = Modifier.weight(1f))
            }
        }
        item { ReportCard("1. Compliant Companies", compliant.map { "${it.company.name}: ${"%.1f".format(it.scoreOutOf10)}/10" }) }
        item { ReportCard("2. Non-Visiting Companies", nonVisiting.map { it.company.name }) }
        item { ReportCard("3. Companies with no registered representatives", noReps.map { it.name }) }
        item { ReportCard("4. Low-Frequency / Weak visiting companies", weak.map { "${it.company.name}: ${"%.1f".format(it.scoreOutOf10)}/10" }) }
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
        item { HeaderCard("Settings & Backup Automation", "Local directory: BORG PHARMACY/BACKUP. Backup runs on launch and every data modification.") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBackup) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(6.dp)); Text("Manual Local Backup") }
                    OutlinedButton(onClick = onRestore, enabled = state.isAdmin) { Text("Restore Local Backup") }
                    OutlinedButton(onClick = onDriveBackup) { Text("Cloud Backup via Google Drive") }
                    OutlinedButton(onClick = onSync) { Text("Sync with Supabase") }
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("User Management (Admin Only)", fontWeight = FontWeight.Bold)
            state.users.forEach { user -> AssistChip(onClick = {}, label = { Text("${user.username} • ${user.role}") }) }
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(passcode, { passcode = it }, label = { Text("Passcode") }, modifier = Modifier.fillMaxWidth())
            RoleDropdown(UserRole.valueOf(role)) { role = it.name }
            Button(onClick = { onCreateUser(username, displayName, UserRole.valueOf(role), passcode); username = ""; displayName = ""; passcode = "" }) {
                Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Create User")
            }
        }
    }
}

@Composable
private fun RoleDropdown(selected: UserRole, onSelected: (UserRole) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected.name) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UserRole.entries.forEach { role -> DropdownMenuItem(text = { Text(role.name) }, onClick = { onSelected(role); expanded = false }) }
        }
    }
}

@Composable
private fun HeaderCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CountCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReportCard(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (lines.isEmpty()) Text("No records.", style = MaterialTheme.typography.bodySmall)
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
