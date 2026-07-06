package com.borgpharmacy.domain

import java.time.LocalDate
import java.util.UUID

/**
 * Open-capacity cyclic scheduler for Borg Pharmacy.
 *
 * Model:
 * - Week 1 is the master board: 5 working days x 2 shifts = 10 master cells.
 * - Every company is assigned once to one master cell.
 * - The same assignment is repeated automatically in Weeks 2, 3 and 4.
 * - There is no maximum cell capacity; new companies go to the least-loaded master cell.
 * - Red line: existing company assignments are never shuffled. We only add missing occurrences,
 *   remove a deleted company, or create a master assignment for a new company.
 */
class ScheduleGenerator(
    private val cycleCalculator: CycleCalculator = CycleCalculator(),
) {
    fun reconcile(
        cycleStart: LocalDate,
        companies: List<Company>,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val scheduler = OpenCapacityCyclicScheduler(cycleStart, existingVisits, cycleCalculator)
        val activeCompanies = companies.filterNot { it.isDeleted }
        val activeIds = activeCompanies.map { it.id }.toSet()

        val deletes = mutableListOf<Visit>()
        val upserts = mutableListOf<Visit>()

        existingVisits
            .filterNot { it.isDeleted }
            .filter { it.cycleStartEpochDay == cycleStart.toEpochDay() }
            .map { it.companyId }
            .distinct()
            .filter { it !in activeIds }
            .forEach { deletes += scheduler.deleteCompanyCompletely(it) }

        activeCompanies
            .sortedWith(compareBy<Company> { it.name.lowercase() }.thenBy { it.id })
            .forEach { company ->
                val plan = scheduler.ensureCompanyRecurringSchedule(company)
                deletes += plan.visitsToSoftDelete
                upserts += plan.visitsToUpsert
            }

        return SchedulePlan(upserts.distinctBy { it.id }, deletes.distinctBy { it.id })
    }

    fun reconcileSingleCompany(
        cycleStart: LocalDate,
        company: Company,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val scheduler = OpenCapacityCyclicScheduler(cycleStart, existingVisits, cycleCalculator)
        return scheduler.ensureCompanyRecurringSchedule(company)
    }

    fun deleteCompanyVisits(
        cycleStart: LocalDate,
        companyId: String,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val scheduler = OpenCapacityCyclicScheduler(cycleStart, existingVisits, cycleCalculator)
        return SchedulePlan(emptyList(), scheduler.deleteCompanyCompletely(companyId))
    }

    class OpenCapacityCyclicScheduler(
        private val cycleStart: LocalDate,
        existingVisits: List<Visit>,
        private val cycleCalculator: CycleCalculator = CycleCalculator(),
    ) {
        private val workingDates: List<LocalDate> = cycleCalculator.workingDatesInCycle(cycleStart).take(20)
        private val dateToWorkingIndex: Map<LocalDate, Int> = workingDates.mapIndexed { index, date -> date to index + 1 }.toMap()
        private val grid: MutableMap<MasterCell, MutableList<Visit>> = mutableMapOf()

        init {
            for (day in 1..5) {
                grid[MasterCell(day, Shift.MORNING)] = mutableListOf()
                grid[MasterCell(day, Shift.EVENING)] = mutableListOf()
            }
            existingVisits
                .filterNot { it.isDeleted }
                .filter { it.cycleStartEpochDay == cycleStart.toEpochDay() }
                .sortedWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.date }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })
                .forEach { visit ->
                    val cell = masterCellForVisit(visit) ?: return@forEach
                    grid[cell]?.add(visit)
                }
        }

        fun ensureCompanyRecurringSchedule(company: Company): SchedulePlan {
            val current = visitsForCompany(company.id)
            val assignment = lockedAssignmentForCompany(company.id) ?: chooseLightestMasterCell()
            val deletes = current.filter { masterCellForVisit(it) != assignment }
            val kept = current.filterNot { visit -> deletes.any { it.id == visit.id } }
            val upserts = mutableListOf<Visit>()

            for (week in 1..4) {
                val alreadyExists = kept.any { it.weekOfCycle == week && masterCellForVisit(it) == assignment }
                if (alreadyExists) continue
                val slotIndex = legacySlotIndexForWeek(assignment, week)
                upserts += createVisit(company.id, assignment, week, slotIndex)
            }

            // Update in-memory grid so a batch operation can add many companies without seeing stale loads.
            deletes.forEach { removed ->
                masterCellForVisit(removed)?.let { cell -> grid[cell]?.removeAll { it.id == removed.id } }
            }
            upserts.forEach { added -> grid[assignment]?.add(added) }
            grid[assignment]?.sortWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.slotIndex })

            return SchedulePlan(upserts, deletes)
        }

        fun deleteCompanyCompletely(companyId: String): List<Visit> {
            val removed = mutableListOf<Visit>()
            grid.values.forEach { visits ->
                val matching = visits.filter { it.companyId == companyId }
                if (matching.isNotEmpty()) {
                    removed += matching
                    visits.removeAll { it.companyId == companyId }
                }
            }
            return removed
        }

        private fun visitsForCompany(companyId: String): List<Visit> = grid.values
            .flatten()
            .filter { it.companyId == companyId }
            .sortedWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.date }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })

        private fun lockedAssignmentForCompany(companyId: String): MasterCell? = visitsForCompany(companyId)
            .firstOrNull { it.weekOfCycle == 1 }
            ?.let { masterCellForVisit(it) }
            ?: visitsForCompany(companyId).firstOrNull()?.let { masterCellForVisit(it) }

        private fun chooseLightestMasterCell(): MasterCell = (1..5)
            .flatMap { day -> listOf(MasterCell(day, Shift.MORNING), MasterCell(day, Shift.EVENING)) }
            .minWith(compareBy<MasterCell> { uniqueCompaniesInCell(it) }.thenBy { it.shift.ordinal }.thenBy { it.dayOfWeekOne })

        private fun uniqueCompaniesInCell(cell: MasterCell): Int = grid[cell].orEmpty().map { it.companyId }.distinct().size

        private fun masterCellForVisit(visit: Visit): MasterCell? {
            val workingIndex = dateToWorkingIndex[visit.date] ?: return null
            val dayInMasterWeek = ((workingIndex - 1) % 5) + 1
            return MasterCell(dayInMasterWeek, visit.shift)
        }

        private fun legacySlotIndexForWeek(cell: MasterCell, week: Int): Int {
            // Supabase legacy schema stores slot_index with a 1..10 check. In the open-capacity
            // model, capacity is unlimited, so this field is kept only as a display/order hint.
            // Multiple companies may share the same legacy slot index after the tenth item.
            val count = grid[cell].orEmpty().count { it.weekOfCycle == week }
            return (count % 10) + 1
        }

        private fun createVisit(companyId: String, cell: MasterCell, week: Int, slotIndex: Int): Visit {
            val workingIndex = ((week - 1) * 5) + cell.dayOfWeekOne
            val date = workingDates[workingIndex - 1]
            val dayOfCycle = cycleCalculator.dayOfCycle(cycleStart, date)
            return Visit(
                id = stableVisitId(companyId, cycleStart.toEpochDay(), cell.dayOfWeekOne, cell.shift, week, slotIndex),
                companyId = companyId,
                cycleStartEpochDay = cycleStart.toEpochDay(),
                dayOfCycle = dayOfCycle,
                weekOfCycle = week,
                date = date,
                shift = cell.shift,
                slotIndex = slotIndex,
            )
        }

        private fun stableVisitId(
            companyId: String,
            cycleStartEpochDay: Long,
            day: Int,
            shift: Shift,
            week: Int,
            slotIndex: Int,
        ): String = UUID.nameUUIDFromBytes(
            "$companyId-$cycleStartEpochDay-open-week-template-$day-${shift.name}-$week-$slotIndex".toByteArray(),
        ).toString()
    }
}

data class SchedulePlan(
    val visitsToUpsert: List<Visit>,
    val visitsToSoftDelete: List<Visit>,
)

private data class MasterCell(
    val dayOfWeekOne: Int, // 1..5 = Sat..Wed in the master week
    val shift: Shift,
)
