package com.borg.pharmacy.domain.usecase

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object SchedulingLogic {

    /**
     * Calculate current cycle and week based on a fixed epoch start date.
     * A cycle is 28 days (4 weeks, Saturday to Friday).
     */
    fun getCurrentCycleInfo(epochStart: LocalDate, currentDate: LocalDate): CycleInfo {
        val daysBetween = ChronoUnit.DAYS.between(epochStart, currentDate)
        val cycleNumber = (daysBetween / 28) + 1
        val dayOfCycle = daysBetween % 28
        val weekNumber = (dayOfCycle / 7) + 1
        
        // Month name can be determined by mapping cycleNumber to actual months or just using current month
        return CycleInfo(
            cycleName = "الدورة ${cycleNumber}",
            weekNumber = weekNumber.toInt(),
            dayOfCycle = dayOfCycle.toInt()
        )
    }

    /**
     * Distribute companies into the 28-day cycle.
     * Category A: 3 visits
     * Category B: 2 visits
     * Category C: 1 visit
     * Day limits: 6-7 Morning, 7-8 Evening.
     * Silently expands to 9-10 if limits exceeded.
     */
    fun distributeVisits(companies: List<Company>): List<ScheduledVisit> {
        val visits = mutableListOf<ScheduledVisit>()
        // In a real implementation, this would load existing visits to apply the "Static Allocation" rule.
        // It would only append new visits to empty slots.
        return visits
    }
}

data class CycleInfo(val cycleName: String, val weekNumber: Int, val dayOfCycle: Int)

data class Company(
    val id: String,
    val name: String,
    val category: String // "A", "B", "C"
)

data class ScheduledVisit(
    val companyId: String,
    val dayOfCycle: Int,
    val shift: String // "Morning" or "Evening"
)
