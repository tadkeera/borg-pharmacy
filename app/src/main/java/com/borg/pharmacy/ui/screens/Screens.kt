package com.borg.pharmacy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScheduleScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "جدول زيارات اليوم", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(text = "الدورة الحالية: أكتوبر - الأسبوع 3")
        // Tables for morning/evening shifts will go here
    }
}

@Composable
fun CompaniesScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "أسماء الشركات", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun EvaluationsScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "تقييم الشركات", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun InquiriesScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "استعلامات", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun DashboardScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "التقارير (Dashboard)", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
    }
}
