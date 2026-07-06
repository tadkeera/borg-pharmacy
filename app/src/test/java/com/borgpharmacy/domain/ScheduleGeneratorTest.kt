package com.borgpharmacy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleGeneratorTest {
    private val start = LocalDate.of(2026, 7, 4) // Saturday 04 July, fixed Borg baseline

    @Test
    fun newCompanyReceivesFourOrthogonalVisits() {
        val company = Company(id = "c1", name = "Alpha", tier = Tier.UNRATED)
        val plan = ScheduleGenerator().reconcile(start, listOf(company), emptyList())
        assertEquals(4, plan.visitsToUpsert.size)
        assertEquals(setOf(1, 2, 3, 4), plan.visitsToUpsert.map { it.weekOfCycle }.toSet())
        assertEquals(4, plan.visitsToUpsert.map { ((it.dayOfCycle - 1) % 7) }.distinct().size)
        assertEquals(2, plan.visitsToUpsert.count { it.shift == Shift.MORNING })
        assertEquals(2, plan.visitsToUpsert.count { it.shift == Shift.EVENING })
    }

    @Test
    fun openCapacityBalancesWeekOneAcrossTenBaseSlots() {
        val companies = (1..130).map { Company(id = "c$it", name = "Company $it", tier = Tier.UNRATED) }
        val plan = ScheduleGenerator().reconcile(start, companies, emptyList())
        assertEquals(520, plan.visitsToUpsert.size)
        val weekOneLoads = plan.visitsToUpsert
            .filter { it.weekOfCycle == 1 }
            .groupBy { ((it.dayOfCycle - 1) % 7) to it.shift }
            .values
            .map { it.size }
        assertEquals(10, weekOneLoads.size)
        assertTrue(weekOneLoads.maxOrNull()!! - weekOneLoads.minOrNull()!! <= 1)
    }

    @Test
    fun deletingCompanyOnlyDeletesItsFourOccurrences() {
        val companies = (1..10).map { Company(id = "c$it", name = "Company $it", tier = Tier.UNRATED) }
        val generated = ScheduleGenerator().reconcile(start, companies, emptyList()).visitsToUpsert
        val plan = ScheduleGenerator().deleteCompanyVisits(start, "c5", generated)
        assertEquals(4, plan.visitsToSoftDelete.size)
        assertTrue(plan.visitsToSoftDelete.all { it.companyId == "c5" })
    }

    @Test
    fun existingWeekOneBaseAssignmentIsLockedAndCompleted() {
        val company = Company(id = "target", name = "Target", tier = Tier.A)
        val existing = Visit(
            id = "v1",
            companyId = company.id,
            cycleStartEpochDay = start.toEpochDay(),
            dayOfCycle = 1,
            weekOfCycle = 1,
            date = start,
            shift = Shift.MORNING,
            slotIndex = 1,
        )
        val plan = ScheduleGenerator().reconcileSingleCompany(start, company, listOf(existing))
        assertEquals(3, plan.visitsToUpsert.size)
        val finalVisits = listOf(existing) + plan.visitsToUpsert
        assertEquals(setOf(1, 2, 3, 4), finalVisits.map { it.weekOfCycle }.toSet())
        assertEquals(4, finalVisits.map { ((it.dayOfCycle - 1) % 7) }.distinct().size)
        assertEquals(2, finalVisits.count { it.shift == Shift.MORNING })
        assertEquals(2, finalVisits.count { it.shift == Shift.EVENING })
    }
}
