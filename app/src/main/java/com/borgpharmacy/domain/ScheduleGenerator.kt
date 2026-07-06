package com.borgpharmacy.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

/**
 * Deterministic 28-day scheduler for Borg Pharmacy.
 *
 * Rules implemented:
 * - The cycle is fixed at 28 calendar days.
 * - Only Saturday-Wednesday receive visits; Thursday/Friday are official weekends.
 * - Morning uses default capacity 7 and silent overflow up to 10.
 * - Evening uses default capacity 8 and silent overflow up to 10.
 * - Existing visits are never shuffled. Upgrades add only the missing visit(s).
 * - Downgrades remove only excess visits, starting with the latest visits, leaving all remaining slots intact.
 * - Company deletion removes all visits for that company; other companies are untouched.
 */
class ScheduleGenerator(
    private val cycleCalculator: CycleCalculator = CycleCalculator(),
    private val morningDefaultCapacity: Int = 7,
    private val eveningDefaultCapacity: Int = 8,
    private val overflowCapacity: Int = 10,
) {
    fun reconcile(
        cycleStart: LocalDate,
        companies: List<Company>,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val activeCompanies = companies.filterNot { it.isDeleted }.filter { it.tier.visitsPerCycle > 0 }
        val activeCompanyIds = activeCompanies.map { it.id }.toSet()
        val cycleStartEpoch = cycleStart.toEpochDay()
        val activeExisting = existingVisits
            .filterNot { it.isDeleted }
            .filter { it.cycleStartEpochDay == cycleStartEpoch }

        val visitsToDelete = mutableListOf<Visit>()
        visitsToDelete += activeExisting.filter { it.companyId !in activeCompanyIds }

        val kept = activeExisting.filterNot { visit -> visitsToDelete.any { it.id == visit.id } }.toMutableList()
        val additions = mutableListOf<Visit>()

        for (company in activeCompanies.sortedBy { it.name.lowercase() }) {
            val current = kept
                .filter { it.companyId == company.id }
                .sortedWith(compareBy<Visit> { it.dayOfCycle }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })
            val expected = company.tier.visitsPerCycle
            when {
                current.size > expected -> {
                    val excess = current.size - expected
                    val removed = current.takeLast(excess)
                    visitsToDelete += removed
                    kept.removeAll { candidate -> removed.any { it.id == candidate.id } }
                }
                current.size < expected -> {
                    val missing = expected - current.size
                    repeat(missing) { missingIndex ->
                        val visitNumber = current.size + additions.count { it.companyId == company.id } + 1
                        val slot = chooseSlot(
                            cycleStart = cycleStart,
                            desiredVisitNumber = visitNumber,
                            expectedVisits = expected,
                            companyId = company.id,
                            occupied = kept + additions,
                        )
                        val newVisit = Visit(
                            id = stableVisitId(company.id, cycleStartEpoch, visitNumber),
                            companyId = company.id,
                            cycleStartEpochDay = cycleStartEpoch,
                            dayOfCycle = cycleCalculator.dayOfCycle(cycleStart, slot.date),
                            weekOfCycle = cycleCalculator.weekOfCycle(cycleCalculator.dayOfCycle(cycleStart, slot.date)),
                            date = slot.date,
                            shift = slot.shift,
                            slotIndex = slot.slotIndex,
                        )
                        additions += newVisit
                    }
                }
            }
        }

        return SchedulePlan(
            visitsToUpsert = additions,
            visitsToSoftDelete = visitsToDelete.distinctBy { it.id },
        )
    }

    private fun chooseSlot(
        cycleStart: LocalDate,
        desiredVisitNumber: Int,
        expectedVisits: Int,
        companyId: String,
        occupied: List<Visit>,
    ): SlotCandidate {
        val targetDay = targetDayForVisit(desiredVisitNumber, expectedVisits)
        val workingDates = cycleCalculator.workingDatesInCycle(cycleStart)

        val candidates = workingDates.flatMap { date ->
            listOf(Shift.MORNING, Shift.EVENING).mapNotNull { shift ->
                val visitsInShift = occupied.filter { it.date == date && it.shift == shift }
                val hasCompanySameDay = occupied.any { it.companyId == companyId && it.date == date }
                if (hasCompanySameDay || visitsInShift.size >= overflowCapacity) {
                    null
                } else {
                    val nextSlot = (visitsInShift.maxOfOrNull { it.slotIndex } ?: 0) + 1
                    val defaultCap = if (shift == Shift.MORNING) morningDefaultCapacity else eveningDefaultCapacity
                    val overflowPenalty = if (nextSlot > defaultCap) 25 else 0
                    val loadPenalty = visitsInShift.size * 2
                    val dayOfCycle = cycleCalculator.dayOfCycle(cycleStart, date)
                    SlotCandidate(
                        date = date,
                        shift = shift,
                        slotIndex = nextSlot,
                        score = abs(dayOfCycle - targetDay) + overflowPenalty + loadPenalty + shiftBias(shift),
                    )
                }
            }
        }

        return candidates.minWithOrNull(compareBy<SlotCandidate> { it.score }.thenBy { it.date }.thenBy { it.shift.ordinal })
            ?: throw IllegalStateException("No available Borg Pharmacy schedule slots up to overflow capacity $overflowCapacity")
    }

    private fun shiftBias(shift: Shift): Int = when (shift) {
        Shift.MORNING -> 0
        Shift.EVENING -> 1
    }

    private fun targetDayForVisit(visitNumber: Int, expectedVisits: Int): Int {
        if (expectedVisits <= 0) return 14
        val fraction = (visitNumber.toDouble() / (expectedVisits + 1).toDouble())
        return (fraction * 28.0).toInt().coerceIn(1, 28)
    }

    private fun stableVisitId(companyId: String, cycleStartEpoch: Long, visitNumber: Int): String =
        UUID.nameUUIDFromBytes("$companyId-$cycleStartEpoch-$visitNumber".toByteArray()).toString()
}

data class SchedulePlan(
    val visitsToUpsert: List<Visit>,
    val visitsToSoftDelete: List<Visit>,
)

private data class SlotCandidate(
    val date: LocalDate,
    val shift: Shift,
    val slotIndex: Int,
    val score: Int,
)
