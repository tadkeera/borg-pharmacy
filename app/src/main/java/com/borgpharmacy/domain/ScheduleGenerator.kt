package com.borgpharmacy.domain

import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Deterministic 28-day scheduler for Borg Pharmacy.
 *
 * Current behavior:
 * - The cycle is fixed at 28 calendar days.
 * - Only Saturday-Wednesday receive visits; Thursday/Friday are official weekends.
 * - Morning default capacity is 7 and Evening default capacity is 8.
 * - Silent overflow still begins after the default capacity, but the hard limit is dynamic so
 *   very large datasets (400+ companies, and even multiple visits per company) can be distributed
 *   without failing the scheduling operation.
 * - Existing visits are never shuffled. Upgrades add only the missing visit(s).
 * - Downgrades remove only excess visits, starting with the latest visits.
 * - Company deletion removes all visits for that company and leaves everyone else untouched.
 */
class ScheduleGenerator(
    private val cycleCalculator: CycleCalculator = CycleCalculator(),
    private val morningDefaultCapacity: Int = 7,
    private val eveningDefaultCapacity: Int = 8,
    private val minimumOverflowCapacity: Int = 10,
) {
    fun reconcile(
        cycleStart: LocalDate,
        companies: List<Company>,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val activeCompanies = companies.filterNot { it.isDeleted }.filter { it.tier.visitsPerCycle > 0 }
        val activeCompanyIds = activeCompanies.map { it.id }.toSet()
        val cycleStartEpoch = cycleStart.toEpochDay()
        val workingDates = cycleCalculator.workingDatesInCycle(cycleStart)
        val dynamicCapacity = dynamicShiftCapacity(activeCompanies, workingDates.size * Shift.entries.size)

        val activeExisting = existingVisits
            .filterNot { it.isDeleted }
            .filter { it.cycleStartEpochDay == cycleStartEpoch }

        val visitsToDelete = mutableListOf<Visit>()
        visitsToDelete += activeExisting.filter { it.companyId !in activeCompanyIds }

        val kept = activeExisting.filterNot { visit -> visitsToDelete.any { it.id == visit.id } }.toMutableList()
        val additions = mutableListOf<Visit>()

        for (company in activeCompanies.sortedWith(compareBy<Company> { stableHash(it.id) }.thenBy { it.name.lowercase() })) {
            val current = kept
                .filter { it.companyId == company.id }
                .sortedWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.date }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })
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
                    repeat(missing) {
                        val visitNumber = current.size + additions.count { it.companyId == company.id } + 1
                        val slot = chooseSlot(
                            cycleStart = cycleStart,
                            workingDates = workingDates,
                            desiredVisitNumber = visitNumber,
                            expectedVisits = expected,
                            companyId = company.id,
                            occupied = kept + additions,
                            dynamicCapacity = dynamicCapacity,
                        )
                        val dayOfCycle = cycleCalculator.dayOfCycle(cycleStart, slot.date)
                        additions += Visit(
                            id = stableVisitId(company.id, cycleStartEpoch, visitNumber),
                            companyId = company.id,
                            cycleStartEpochDay = cycleStartEpoch,
                            dayOfCycle = dayOfCycle,
                            weekOfCycle = cycleCalculator.weekOfCycle(dayOfCycle),
                            date = slot.date,
                            shift = slot.shift,
                            slotIndex = slot.slotIndex,
                        )
                    }
                }
            }
        }

        return SchedulePlan(
            visitsToUpsert = additions,
            visitsToSoftDelete = visitsToDelete.distinctBy { it.id },
        )
    }

    private fun dynamicShiftCapacity(activeCompanies: List<Company>, shiftBuckets: Int): Int {
        val totalVisits = activeCompanies.sumOf { it.tier.visitsPerCycle }
        if (shiftBuckets <= 0) return minimumOverflowCapacity
        // +2 creates breathing room and prevents unnecessary hard failures with uneven tiers.
        return max(minimumOverflowCapacity, ceil(totalVisits.toDouble() / shiftBuckets.toDouble()).toInt() + 2)
    }

    private fun chooseSlot(
        cycleStart: LocalDate,
        workingDates: List<LocalDate>,
        desiredVisitNumber: Int,
        expectedVisits: Int,
        companyId: String,
        occupied: List<Visit>,
        dynamicCapacity: Int,
    ): SlotCandidate {
        val targetIndex = targetWorkingDateIndex(companyId, desiredVisitNumber, expectedVisits, workingDates.size)
        val targetShift = targetShift(companyId, desiredVisitNumber)

        val candidates = workingDates.flatMapIndexed { dateIndex, date ->
            Shift.entries.mapNotNull { shift ->
                val visitsInShift = occupied.filter { it.date == date && it.shift == shift }
                val hasCompanySameDay = occupied.any { it.companyId == companyId && it.date == date }
                if (hasCompanySameDay || visitsInShift.size >= dynamicCapacity) {
                    null
                } else {
                    val nextSlot = (visitsInShift.maxOfOrNull { it.slotIndex } ?: 0) + 1
                    val defaultCap = if (shift == Shift.MORNING) morningDefaultCapacity else eveningDefaultCapacity
                    val overflowPenalty = if (nextSlot > defaultCap) 20 else 0
                    val loadPenalty = visitsInShift.size * 14
                    val targetDistancePenalty = abs(dateIndex - targetIndex) * 3
                    val shiftPenalty = if (shift == targetShift) 0 else 4
                    val weekNumber = cycleCalculator.weekOfCycle(cycleCalculator.dayOfCycle(cycleStart, date))
                    val sameCompanyWeekPenalty = if (occupied.any { it.companyId == companyId && it.weekOfCycle == weekNumber }) 30 else 0
                    SlotCandidate(
                        date = date,
                        shift = shift,
                        slotIndex = nextSlot,
                        score = loadPenalty + targetDistancePenalty + shiftPenalty + overflowPenalty + sameCompanyWeekPenalty,
                    )
                }
            }
        }

        return candidates.minWithOrNull(compareBy<SlotCandidate> { it.score }.thenBy { it.date }.thenBy { it.shift.ordinal })
            ?: throw IllegalStateException("No available Borg Pharmacy schedule slots; dynamic capacity $dynamicCapacity was exhausted")
    }

    private fun targetWorkingDateIndex(companyId: String, visitNumber: Int, expectedVisits: Int, workingDays: Int): Int {
        if (workingDays <= 1) return 0
        val visitCount = expectedVisits.coerceAtLeast(1)
        val segmentStart = ((visitNumber - 1) * workingDays) / visitCount
        val segmentExclusiveEnd = (visitNumber * workingDays) / visitCount
        val span = (segmentExclusiveEnd - segmentStart).coerceAtLeast(1)
        val offset = abs(stableHash("$companyId-$visitNumber-date")) % span
        return (segmentStart + offset).coerceIn(0, workingDays - 1)
    }

    private fun targetShift(companyId: String, visitNumber: Int): Shift =
        if (abs(stableHash("$companyId-$visitNumber-shift")) % 2 == 0) Shift.MORNING else Shift.EVENING

    private fun stableHash(value: String): Int = value.fold(0) { acc, char -> (acc * 31) + char.code } and Int.MAX_VALUE

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
