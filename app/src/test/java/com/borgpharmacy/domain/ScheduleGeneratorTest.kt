package com.borgpharmacy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleGeneratorTest {
    private val start = LocalDate.of(2026, 7, 4) // Saturday 04 July, fixed Borg baseline

    @Test
    fun tierAReceivesThreeVisitsOnDifferentWorkingDaysAndWeeksWhenPossible() {
        val company = Company(id = "c1", name = "Alpha", tier = Tier.A)
        val plan = ScheduleGenerator().reconcile(start, listOf(company), emptyList())
        assertEquals(3, plan.visitsToUpsert.size)
        assertTrue(plan.visitsToUpsert.all { it.date.dayOfWeek.isBorgWorkingDay() })
        assertEquals(3, plan.visitsToUpsert.map { it.date }.distinct().size)
        assertEquals(3, plan.visitsToUpsert.map { it.weekOfCycle }.distinct().size)
    }

    @Test
    fun downgradeRemovesOnlyOneVisitAndNeverAddsOrShuffles() {
        val companyA = Company(id = "c1", name = "Alpha", tier = Tier.A)
        val first = ScheduleGenerator().reconcile(start, listOf(companyA), emptyList()).visitsToUpsert
        val companyB = companyA.copy(tier = Tier.B)
        val second = ScheduleGenerator().reconcile(start, listOf(companyB), first)
        assertEquals(1, second.visitsToSoftDelete.size)
        assertEquals(0, second.visitsToUpsert.size)
        assertTrue(second.visitsToSoftDelete.first().id in first.map { it.id })
    }

    @Test
    fun ordinaryLoadStaysWithinAbsoluteEmergencyCapacity() {
        val companies = (1..70).map { Company(id = "c$it", name = "Company $it", tier = Tier.A) }
        val plan = ScheduleGenerator().reconcile(start, companies, emptyList())
        val maxLoad = plan.visitsToUpsert.groupBy { it.date to it.shift }.maxOf { it.value.size }
        assertTrue(maxLoad <= 10)
    }

    @Test
    fun matrixCapacitySupportsFourHundredSingleVisitCompanies() {
        val companies = (1..400).map { Company(id = "c$it", name = "Company $it", tier = Tier.C) }
        val plan = ScheduleGenerator().reconcile(start, companies, emptyList())
        assertEquals(400, plan.visitsToUpsert.size)
        val loads = plan.visitsToUpsert.groupBy { it.date to it.shift }.values.map { it.size }
        assertEquals(40, loads.size)
        assertTrue(loads.all { it == 10 })
    }

    @Test
    fun addingVisitChoosesLightestCellAndAvoidsSameDay() {
        val company = Company(id = "target", name = "Target", tier = Tier.B)
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
        assertEquals(1, plan.visitsToUpsert.size)
        assertFalse(plan.visitsToUpsert.first().date == start)
    }
}
