package com.borgpharmacy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleGeneratorTest {
    private val start = LocalDate.of(2026, 7, 4) // Saturday 04 July, fixed Borg baseline

    @Test
    fun newCompanyReceivesOneMasterSlotRepeatedAcrossFourWeeks() {
        val company = Company(id = "c1", name = "Alpha", tier = Tier.UNRATED)
        val plan = ScheduleGenerator().reconcile(start, listOf(company), emptyList())
        assertEquals(4, plan.visitsToUpsert.size)
        assertEquals(setOf(1, 2, 3, 4), plan.visitsToUpsert.map { it.weekOfCycle }.toSet())
        assertEquals(1, plan.visitsToUpsert.map { it.shift }.distinct().size)
        assertEquals(1, plan.visitsToUpsert.map { ((it.dayOfCycle - 1) % 7) }.distinct().size)
    }

    @Test
    fun openCapacityBalancesAcrossTenMasterSlots() {
        val companies = (1..130).map { Company(id = "c$it", name = "Company $it", tier = Tier.UNRATED) }
        val plan = ScheduleGenerator().reconcile(start, companies, emptyList())
        assertEquals(520, plan.visitsToUpsert.size)
        val weekOneLoads = plan.visitsToUpsert
            .filter { it.weekOfCycle == 1 }
            .groupBy { it.date to it.shift }
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
    fun existingAssignmentIsLockedAndMissingWeeksAreFilledOnly() {
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
        assertTrue(plan.visitsToUpsert.all { it.shift == Shift.MORNING })
        assertTrue(plan.visitsToUpsert.all { ((it.dayOfCycle - 1) % 7) == 0 })
        assertEquals(0, plan.visitsToSoftDelete.size)
    }
}
