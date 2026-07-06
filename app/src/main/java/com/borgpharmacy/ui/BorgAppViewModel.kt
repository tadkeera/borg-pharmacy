package com.borgpharmacy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.borgpharmacy.AppContainer
import com.borgpharmacy.data.local.TierCountTuple
import com.borgpharmacy.data.repository.BorgRepository
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.CompanyReportScore
import com.borgpharmacy.domain.CycleCalculator
import com.borgpharmacy.domain.CycleInfo
import com.borgpharmacy.domain.PrintCount
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.Tier
import com.borgpharmacy.domain.UserAccount
import com.borgpharmacy.domain.UserRole
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.VisitStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class BorgAppViewModel(
    private val repository: BorgRepository,
    private val cycleCalculator: CycleCalculator,
) : ViewModel() {
    private val _state = MutableStateFlow(BorgUiState())
    val state: StateFlow<BorgUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val start = repository.initialize()
            _state.update { it.copy(initialized = true, cycleStart = start, cycleInfo = cycleCalculator.currentCycle(start)) }
            refreshReports()
        }
        viewModelScope.launch { repository.observeCompanies().collect { value -> _state.update { it.copy(companies = value) } } }
        viewModelScope.launch { repository.observeRepresentatives().collect { value -> _state.update { it.copy(representatives = value) } } }
        viewModelScope.launch { repository.observeVisits().collect { value -> _state.update { it.copy(visits = value) } } }
        viewModelScope.launch { repository.observePrintCounts().collect { value -> _state.update { it.copy(printCounts = value) } } }
        viewModelScope.launch { repository.observeUsers().collect { value -> _state.update { it.copy(users = value) } } }
        viewModelScope.launch { repository.observeTierCounts().collect { value -> _state.update { it.copy(tierCounts = value) } } }
    }

    fun login(username: String, passcode: String) = viewModelScope.launch {
        val user = repository.login(username, passcode)
        if (user == null) {
            _state.update { it.copy(loginError = "اسم المستخدم أو كود التفعيل غير صحيح") }
        } else {
            _state.update { it.copy(currentUser = user, loginError = null, mustChangePasscodeUser = if (user.mustChangePasscode) user else null) }
        }
    }

    fun changeForcedPasscode(newPasscode: String) = viewModelScope.launch {
        val user = _state.value.mustChangePasscodeUser ?: return@launch
        if (newPasscode.length < 6) {
            _state.update { it.copy(loginError = "يجب أن يكون الكود الجديد 6 أحرف على الأقل") }
            return@launch
        }
        repository.changePasscode(user.id, newPasscode)
        _state.update { it.copy(currentUser = user.copy(mustChangePasscode = false), mustChangePasscodeUser = null, loginError = null) }
    }

    fun logout() {
        _state.update { it.copy(currentUser = null) }
    }

    fun addCompany(name: String) = adminOnly {
        repository.addCompany(name)
        snackbar("تمت إضافة الشركة")
    }

    fun importCompaniesCsv(csv: String) = adminOnly {
        val count = repository.importCompaniesCsv(csv)
        snackbar("تم استيراد $count شركة")
    }

    fun updateTier(companyId: String, tier: Tier) = adminOnly {
        repository.updateCompanyTier(companyId, tier)
    }

    fun saveEvaluationsAndSchedule() = adminOnly {
        repository.rescheduleCurrentCycle()
        refreshReports()
        snackbar("تم حفظ التقييمات وتوليد جدول دورة 28 يومًا")
    }

    fun deleteCompany(companyId: String) = adminOnly {
        repository.deleteCompany(companyId)
        refreshReports()
        snackbar("تم حذف الشركة وإزالة جميع زياراتها")
    }

    fun addRepresentative(companyId: String, name: String, phone: String) = adminOnly {
        repository.addRepresentative(companyId, name, phone)
        snackbar("تم حفظ المندوب")
    }

    fun deleteRepresentative(repId: String) = adminOnly {
        repository.deleteRepresentative(repId)
        snackbar("تم حذف المندوب")
    }

    fun createUser(username: String, displayName: String, role: UserRole, passcode: String) = adminOnly {
        repository.createUser(username, displayName, role, passcode)
        snackbar("تم إنشاء المستخدم")
    }

    fun markVisitStatus(visitId: String, status: VisitStatus) = adminOnly {
        repository.setVisitStatus(visitId, status)
        refreshReports()
    }

    fun recordPrint(repId: String, visitId: String) = viewModelScope.launch {
        repository.recordPrint(repId, visitId)
    }

    fun backupNow() = viewModelScope.launch {
        repository.backupNow("manual")
        snackbar("تم إنشاء نسخة احتياطية داخل BORG PHARMACY/BACKUP")
    }

    fun syncNow() = viewModelScope.launch {
        repository.syncNow()
        snackbar("تم طلب المزامنة السحابية")
    }

    fun clearSnackbar() = _state.update { it.copy(message = null) }

    private fun adminOnly(block: suspend () -> Unit) = viewModelScope.launch {
        if (_state.value.currentUser?.role != UserRole.ADMIN) {
            snackbar("صلاحية الصيدلي للقراءة والطباعة فقط ولا تسمح بالتعديل")
            return@launch
        }
        block()
    }

    private suspend fun refreshReports() {
        _state.update { it.copy(dashboardScores = repository.dashboardScores()) }
    }

    private fun snackbar(message: String) {
        _state.update { it.copy(message = message) }
    }
}

data class BorgUiState(
    val initialized: Boolean = false,
    val cycleStart: LocalDate = LocalDate.now(),
    val cycleInfo: CycleInfo = CycleCalculator().currentCycle(LocalDate.now()),
    val currentUser: UserAccount? = null,
    val mustChangePasscodeUser: UserAccount? = null,
    val loginError: String? = null,
    val companies: List<Company> = emptyList(),
    val representatives: List<Representative> = emptyList(),
    val visits: List<Visit> = emptyList(),
    val printCounts: List<PrintCount> = emptyList(),
    val users: List<UserAccount> = emptyList(),
    val tierCounts: List<TierCountTuple> = emptyList(),
    val dashboardScores: List<CompanyReportScore> = emptyList(),
    val message: String? = null,
) {
    val isUnlocked: Boolean get() = currentUser != null && mustChangePasscodeUser == null
    val isAdmin: Boolean get() = currentUser?.role == UserRole.ADMIN
    val repsByCompany: Map<String, List<Representative>> get() = representatives.groupBy { it.companyId }
    val visitsByCompany: Map<String, List<Visit>> get() = visits.groupBy { it.companyId }
    val printCountMap: Map<Pair<String, String>, Int> get() = printCounts.associate { (it.repId to it.visitId) to it.count }
}

class BorgViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BorgAppViewModel(container.repository, container.cycleCalculator) as T
    }
}
