package com.borgpharmacy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleGeneratorTest {
    private val start = LocalDate.of(2021, 7, 3) // Saturday 03 July, fixed Borg baseline

    @Test
    fun tierAReceivesThreeVisits() {
        val company = Company(id = "c1", name = "Alpha", tier = Tier.A)
        val plan = ScheduleGenerator().reconcile(start, listOf(company), emptyList())
        assertEquals(3, plan.visitsToUpsert.size)
        assertTrue(plan.visitsToUpsert.all { it.date.dayOfWeek.isBorgWorkingDay() })
    }

    @Test
    fun downgradeRemovesOnlyExcessVisit() {
        val companyA = Company(id = "c1", name = "Alpha", tier = Tier.A)
        val first = ScheduleGenerator().reconcile(start, listOf(companyA), emptyList()).visitsToUpsert
        val companyB = companyA.copy(tier = Tier.B)
        val second = ScheduleGenerator().reconcile(start, listOf(companyB), first)
        assertEquals(1, second.visitsToSoftDelete.size)
        assertEquals(0, second.visitsToUpsert.size)
    }

    @Test
    fun ordinaryLoadStaysWithinClassicOverflowCapacity() {
        val companies = (1..70).map { Company(id = "c$it", name = "Company $it", tier = Tier.A) }
        val plan = ScheduleGenerator().reconcile(start, companies, emptyList())
        val maxLoad = plan.visitsToUpsert.groupBy { it.date to it.shift }.maxOf { it.value.size }
        assertTrue(maxLoad <= 10)
    }

    @Test
    fun veryLargeCompanyListIsDistributedWithoutFailure() {
        val companies = (1..450).map { Company(id = "c$it", name = "Company $it", tier = Tier.C) }
        val plan = ScheduleGenerator().reconcile(start, companies, emptyList())
        assertEquals(450, plan.visitsToUpsert.size)
        val loads = plan.visitsToUpsert.groupBy { it.date to it.shift }.values.map { it.size }
        assertTrue(loads.maxOrNull()!! - loads.minOrNull()!! <= 4)
    }
}
