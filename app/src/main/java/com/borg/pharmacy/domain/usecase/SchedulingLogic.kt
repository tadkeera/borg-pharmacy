package com.borg.pharmacy.domain.usecase

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek

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
     * Working days: Saturday to Wednesday. (Thursday and Friday are holidays).
     */
    fun distributeVisits(companies: List<Company>, existingVisits: List<ScheduledVisit>): List<ScheduledVisit> {
        val newVisits = existingVisits.toMutableList()
        
        // Define working days in the 28-day cycle (0 to 27)
        // Assuming day 0 is Saturday.
        // Week 1: 0(Sat)-4(Wed) are working, 5(Thu)-6(Fri) are holiday
        // Week 2: 7-11 working, 12-13 holiday
        // Week 3: 14-18 working, 19-20 holiday
        // Week 4: 21-25 working, 26-27 holiday
        
        val workingDays = mutableListOf<Int>()
        for (day in 0..27) {
            val dayOfWeekIndex = day % 7 // 0 = Sat, 1 = Sun, 2 = Mon, 3 = Tue, 4 = Wed, 5 = Thu, 6 = Fri
            if (dayOfWeekIndex <= 4) {
                workingDays.add(day)
            }
        }
        
        // Count visits required per company based on Category
        val requiredVisits = companies.associateWith { company ->
            when (company.category.uppercase()) {
                "A" -> 3
                "B" -> 2
                "C" -> 1
                else -> 0
            }
        }
        
        for ((company, required) in requiredVisits) {
            val companyVisits = newVisits.count { it.companyId == company.id }
            if (companyVisits < required) {
                val visitsToAdd = required - companyVisits
                for (i in 0 until visitsToAdd) {
                    // Find an empty slot
                    val slot = findAvailableSlot(workingDays, newVisits)
                    if (slot != null) {
                        newVisits.add(ScheduledVisit(company.id, slot.day, slot.shift))
                    } else {
                        // Emergency allocation: add silently even if exceeded limit
                        val randomDay = workingDays.random()
                        newVisits.add(ScheduledVisit(company.id, randomDay, "Morning"))
                    }
                }
            } else if (companyVisits > required) {
                // E.g. downgraded from A to B. Remove exactly (companyVisits - required) visits.
                val visitsToRemove = companyVisits - required
                val companyExistingVisits = newVisits.filter { it.companyId == company.id }.takeLast(visitsToRemove)
                newVisits.removeAll(companyExistingVisits)
            }
        }

        return newVisits
    }

    private fun findAvailableSlot(workingDays: List<Int>, scheduled: List<ScheduledVisit>): Slot? {
        // Defaults: 6 Morning, 7 Evening
        for (day in workingDays) {
            val morningCount = scheduled.count { it.dayOfCycle == day && it.shift == "Morning" }
            if (morningCount < 6) return Slot(day, "Morning")
            
            val eveningCount = scheduled.count { it.dayOfCycle == day && it.shift == "Evening" }
            if (eveningCount < 7) return Slot(day, "Evening")
        }
        return null
    }
}

data class Slot(val day: Int, val shift: String)

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
