package com.borg.pharmacy.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "schedule",
                    onClick = { navController.navigate("schedule") },
                    icon = { Text("📅") },
                    label = { Text("الجدول") }
                )
                NavigationBarItem(
                    selected = currentRoute == "companies",
                    onClick = { navController.navigate("companies") },
                    icon = { Text("🏢") },
                    label = { Text("الشركات") }
                )
                NavigationBarItem(
                    selected = currentRoute == "evaluations",
                    onClick = { navController.navigate("evaluations") },
                    icon = { Text("⭐") },
                    label = { Text("التقييم") }
                )
                NavigationBarItem(
                    selected = currentRoute == "inquiries",
                    onClick = { navController.navigate("inquiries") },
                    icon = { Text("🔍") },
                    label = { Text("استعلام") }
                )
                NavigationBarItem(
                    selected = currentRoute == "dashboard",
                    onClick = { navController.navigate("dashboard") },
                    icon = { Text("📊") },
                    label = { Text("التقارير") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "schedule",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("schedule") { ScheduleScreen() }
            composable("companies") { CompaniesScreen() }
            composable("evaluations") { EvaluationsScreen() }
            composable("inquiries") { InquiriesScreen() }
            composable("dashboard") { DashboardScreen() }
        }
    }
}
