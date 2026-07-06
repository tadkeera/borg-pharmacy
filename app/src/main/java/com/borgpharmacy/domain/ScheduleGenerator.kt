package com.borgpharmacy.domain

import java.time.LocalDate
import java.util.UUID

/**
 * Borg Pharmacy matrix scheduler.
 *
 * The cycle schedule is represented as a fixed 20 x 2 matrix:
 * - 20 columns = the real working days in the 28-day cycle (Saturday-Wednesday for four weeks).
 * - 2 rows = Morning (row 0) and Evening (row 1).
 *
 * Each cell contains the fixed ordered visits/company IDs already assigned to that working day and shift.
 * The red-line rule is enforced here: no operation shuffles or recalculates existing visits. We only:
 * - add new visits into the lightest available cells;
 * - prune excess visits from the heaviest cells occupied by that company;
 * - remove a deleted company from every cell.
 */
class ScheduleGenerator(
    private val cycleCalculator: CycleCalculator = CycleCalculator(),
    private val morningDefaultMin: Int = 6,
    private val morningDefaultMax: Int = 7,
    private val eveningDefaultMin: Int = 7,
    private val eveningDefaultMax: Int = 8,
    private val absoluteCellMax: Int = 10,
) {
    fun reconcile(
        cycleStart: LocalDate,
        companies: List<Company>,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val matrix = HealthcareScheduler(cycleStart, existingVisits, cycleCalculator, absoluteCellMax)
        val activeCompanies = companies.filterNot { it.isDeleted }
        val activeIds = activeCompanies.map { it.id }.toSet()
        val visitsToDelete = mutableListOf<Visit>()
        val visitsToAdd = mutableListOf<Visit>()

        val removedCompanies = existingVisits
            .filterNot { it.isDeleted }
            .filter { it.companyId !in activeIds || activeCompanies.firstOrNull { company -> company.id == it.companyId }?.tier?.visitsPerCycle == 0 }
        removedCompanies.map { it.companyId }.distinct().forEach { companyId ->
            visitsToDelete += matrix.deleteCompanyCompletely(companyId)
        }

        activeCompanies
            .filter { it.tier.visitsPerCycle > 0 }
            .sortedWith(compareBy<Company> { it.tier.ordinal }.thenBy { it.name.lowercase() }.thenBy { it.id })
            .forEach { company ->
                val plan = reconcileSingleCompany(matrix, company)
                visitsToDelete += plan.visitsToSoftDelete
                visitsToAdd += plan.visitsToUpsert
            }

        return SchedulePlan(
            visitsToUpsert = visitsToAdd.distinctBy { it.id },
            visitsToSoftDelete = visitsToDelete.distinctBy { it.id },
        )
    }

    fun reconcileSingleCompany(
        cycleStart: LocalDate,
        company: Company,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val matrix = HealthcareScheduler(cycleStart, existingVisits, cycleCalculator, absoluteCellMax)
        return reconcileSingleCompany(matrix, company)
    }

    fun deleteCompanyVisits(
        cycleStart: LocalDate,
        companyId: String,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val matrix = HealthcareScheduler(cycleStart, existingVisits, cycleCalculator, absoluteCellMax)
        return SchedulePlan(
            visitsToUpsert = emptyList(),
            visitsToSoftDelete = matrix.deleteCompanyCompletely(companyId),
        )
    }

    private fun reconcileSingleCompany(matrix: HealthcareScheduler, company: Company): SchedulePlan {
        val expectedVisits = company.tier.visitsPerCycle
        val currentVisits = matrix.visitsForCompany(company.id)
        return when {
            currentVisits.size < expectedVisits -> SchedulePlan(
                visitsToUpsert = matrix.allocateVisitsForCompany(company, expectedVisits - currentVisits.size),
                visitsToSoftDelete = emptyList(),
            )
            currentVisits.size > expectedVisits -> SchedulePlan(
                visitsToUpsert = emptyList(),
                visitsToSoftDelete = matrix.downgradeCompanyVisits(company.id, currentVisits.size - expectedVisits),
            )
            else -> SchedulePlan(emptyList(), emptyList())
        }
    }

    data class ScheduledVisit(
        val dayIndex: Int, // 1..20 working day index, not calendar day-of-cycle
        val isEvening: Boolean,
    )

    /** Matrix implementation requested by the administrator. */
    class HealthcareScheduler(
        private val cycleStart: LocalDate,
        existingVisits: List<Visit>,
        private val cycleCalculator: CycleCalculator = CycleCalculator(),
        private val absoluteCellMax: Int = 10,
    ) {
        private val workingDates: List<LocalDate> = cycleCalculator.workingDatesInCycle(cycleStart).take(20)
        private val dateToWorkingDayIndex: Map<LocalDate, Int> = workingDates.mapIndexed { index, date -> date to index + 1 }.toMap()
        private val globalGrid: MutableMap<CellKey, MutableList<Visit>> = mutableMapOf()

        init {
            for (day in 1..20) {
                globalGrid[CellKey(day, false)] = mutableListOf()
                globalGrid[CellKey(day, true)] = mutableListOf()
            }

            existingVisits
                .filterNot { it.isDeleted }
                .filter { it.cycleStartEpochDay == cycleStart.toEpochDay() }
                .sortedWith(compareBy<Visit> { it.date }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })
                .forEach { visit ->
                    val dayIndex = dateToWorkingDayIndex[visit.date] ?: return@forEach
                    val key = CellKey(dayIndex, visit.shift == Shift.EVENING)
                    globalGrid[key]?.add(visit)
                }
        }

        fun visitsForCompany(companyId: String): List<Visit> = globalGrid.values
            .flatten()
            .filter { it.companyId == companyId }
            .sortedWith(compareBy<Visit> { workingDayIndex(it) }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })

        fun allocateVisitsForCompany(company: Company, requiredNewVisits: Int): List<Visit> {
            val allocated = mutableListOf<Visit>()
            repeat(requiredNewVisits) {
                val bestCell = bestCellForCompany(company.id) ?: return@repeat
                val date = workingDates[bestCell.dayIndex - 1]
                val shift = if (bestCell.isEvening) Shift.EVENING else Shift.MORNING
                val slotIndex = firstVacantSlot(globalGrid[bestCell].orEmpty().map { it.slotIndex }.toSet())
                    ?: return@repeat
                val calendarDayOfCycle = cycleCalculator.dayOfCycle(cycleStart, date)
                val visit = Visit(
                    id = stableVisitId(company.id, cycleStart.toEpochDay(), bestCell.dayIndex, bestCell.isEvening, slotIndex),
                    companyId = company.id,
                    cycleStartEpochDay = cycleStart.toEpochDay(),
                    dayOfCycle = calendarDayOfCycle,
                    weekOfCycle = cycleCalculator.weekOfCycle(calendarDayOfCycle),
                    date = date,
                    shift = shift,
                    slotIndex = slotIndex,
                )
                globalGrid[bestCell]?.add(visit)
                globalGrid[bestCell]?.sortBy { it.slotIndex }
                allocated += visit
            }
            return allocated
        }

        fun downgradeCompanyVisits(companyId: String, visitsToRemove: Int): List<Visit> {
            val removed = mutableListOf<Visit>()
            repeat(visitsToRemove) {
                val candidate = visitsForCompany(companyId)
                    .mapNotNull { visit ->
                        val key = cellKeyForVisit(visit) ?: return@mapNotNull null
                        VisitRemovalCandidate(
                            visit = visit,
                            key = key,
                            weight = cellWeight(key),
                        )
                    }
                    .maxWithOrNull(
                        compareBy<VisitRemovalCandidate> { it.weight }
                            .thenBy { it.key.isEvening } // evening usually carries the higher default load
                            .thenBy { it.key.dayIndex }
                            .thenBy { it.visit.slotIndex }
                    ) ?: return@repeat

                globalGrid[candidate.key]?.removeAll { it.id == candidate.visit.id }
                removed += candidate.visit
            }
            return removed
        }

        fun deleteCompanyCompletely(companyId: String): List<Visit> {
            val removed = mutableListOf<Visit>()
            globalGrid.forEach { (_, visits) ->
                val matching = visits.filter { it.companyId == companyId }
                if (matching.isNotEmpty()) {
                    removed += matching
                    visits.removeAll { it.companyId == companyId }
                }
            }
            return removed
        }

        private fun bestCellForCompany(companyId: String): CellKey? {
            val companyVisits = visitsForCompany(companyId)
            val occupiedDays = companyVisits.mapNotNull { workingDayIndex(it) }.toSet()
            val occupiedWeeks = companyVisits.map { it.weekOfCycle }.toSet()

            val candidates = (1..20).flatMap { day ->
                listOf(false, true).mapNotNull { isEvening ->
                    if (day in occupiedDays) return@mapNotNull null
                    val key = CellKey(day, isEvening)
                    if (cellWeight(key) >= absoluteCellMax) return@mapNotNull null
                    val week = ((day - 1) / 5) + 1
                    CellCandidate(
                        key = key,
                        weight = cellWeight(key),
                        week = week,
                        hasDifferentWeekPriority = week !in occupiedWeeks,
                    )
                }
            }
            if (candidates.isEmpty()) return null

            val preferredWeekCandidates = candidates.filter { it.hasDifferentWeekPriority }
                .takeIf { it.isNotEmpty() }
                ?: candidates

            return preferredWeekCandidates
                .minWithOrNull(
                    compareBy<CellCandidate> { it.weight }
                        .thenBy { it.key.isEvening } // false/morning first, then true/evening
                        .thenBy { it.week }
                        .thenBy { it.key.dayIndex }
                )
                ?.key
        }

        private fun cellWeight(key: CellKey): Int = globalGrid[key]?.size ?: 0

        private fun cellKeyForVisit(visit: Visit): CellKey? {
            val day = workingDayIndex(visit) ?: return null
            return CellKey(day, visit.shift == Shift.EVENING)
        }

        private fun workingDayIndex(visit: Visit): Int? = dateToWorkingDayIndex[visit.date]

        private fun firstVacantSlot(usedSlots: Set<Int>): Int? = (1..absoluteCellMax).firstOrNull { it !in usedSlots }

        private fun stableVisitId(
            companyId: String,
            cycleStartEpochDay: Long,
            dayIndex: Int,
            isEvening: Boolean,
            slotIndex: Int,
        ): String = UUID.nameUUIDFromBytes(
            "$companyId-$cycleStartEpochDay-matrix-$dayIndex-$isEvening-$slotIndex".toByteArray(),
        ).toString()
    }
}

data class SchedulePlan(
    val visitsToUpsert: List<Visit>,
    val visitsToSoftDelete: List<Visit>,
)

private data class CellKey(
    val dayIndex: Int,
    val isEvening: Boolean,
)

private data class CellCandidate(
    val key: CellKey,
    val weight: Int,
    val week: Int,
    val hasDifferentWeekPriority: Boolean,
)

private data class VisitRemovalCandidate(
    val visit: Visit,
    val key: CellKey,
    val weight: Int,
)
