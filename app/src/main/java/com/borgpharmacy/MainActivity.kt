package com.borgpharmacy

import android.Manifest
import android.content.Intent
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
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.print.PassPrintManager
import com.borgpharmacy.ui.BorgAppViewModel
import com.borgpharmacy.ui.BorgViewModelFactory
import com.borgpharmacy.ui.screens.BorgApp
import com.borgpharmacy.ui.theme.BorgPharmacyTheme
import kotlinx.coroutines.launch

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
                onSaveTierChanges = viewModel::saveTierChanges,
                onUpdateCompanyName = viewModel::updateCompanyName,
                onDeleteCompany = viewModel::deleteCompany,
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
