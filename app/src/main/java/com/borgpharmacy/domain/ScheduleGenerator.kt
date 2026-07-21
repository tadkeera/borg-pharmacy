package com.borgpharmacy.domain

import java.time.LocalDate
import java.util.UUID

/**
 * Orthogonal Rotation cyclic scheduler for Borg Pharmacy.
 *
 * The schedule has a Week-1 master blueprint (5 working days × 2 shifts = 10 base cells).
 * Each company is locked to one base cell forever unless deleted. The 4-week cycle is generated
 * from that base cell using deterministic orthogonal rotation:
 *
 * Week 1: base day + 0, base shift
 * Week 2: base day + 1, inverted shift
 * Week 3: base day + 2, base shift
 * Week 4: base day + 3, inverted shift
 *
 * This gives every company exactly four visits per cycle, across four different working days,
 * and exactly two morning + two evening visits. No automatic shuffling of locked companies occurs.
 */
class ScheduleGenerator(
    private val cycleCalculator: CycleCalculator = CycleCalculator(),
) {
    fun reconcile(
        cycleStart: LocalDate,
        companies: List<Company>,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val scheduler = OrthogonalRotationScheduler(cycleStart, existingVisits, cycleCalculator)
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
            .forEach { companyId -> deletes += scheduler.deleteCompanyCompletely(companyId) }

        activeCompanies
            .sortedWith(compareBy<Company> { it.name.lowercase() }.thenBy { it.id })
            .forEach { company ->
                val plan = scheduler.ensureCompanyRotation(company)
                deletes += plan.visitsToSoftDelete
                upserts += plan.visitsToUpsert
            }

        return SchedulePlan(
            visitsToUpsert = upserts.distinctBy { it.id },
            visitsToSoftDelete = deletes.distinctBy { it.id },
        )
    }

    fun reconcileSingleCompany(
        cycleStart: LocalDate,
        company: Company,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val scheduler = OrthogonalRotationScheduler(cycleStart, existingVisits, cycleCalculator)
        return scheduler.ensureCompanyRotation(company)
    }

    fun deleteCompanyVisits(
        cycleStart: LocalDate,
        companyId: String,
        existingVisits: List<Visit>,
    ): SchedulePlan {
        val scheduler = OrthogonalRotationScheduler(cycleStart, existingVisits, cycleCalculator)
        return SchedulePlan(emptyList(), scheduler.deleteCompanyCompletely(companyId))
    }

    class OrthogonalRotationScheduler(
        private val cycleStart: LocalDate,
        existingVisits: List<Visit>,
        private val cycleCalculator: CycleCalculator = CycleCalculator(),
    ) {
        private val workingDates: List<LocalDate> = cycleCalculator.workingDatesInCycle(cycleStart).take(20)
        private val dateToWorkingIndex: Map<LocalDate, Int> = workingDates.mapIndexed { index, date -> date to index + 1 }.toMap()
        private val grid: MutableMap<BaseCell, MutableList<Visit>> = mutableMapOf()

        init {
            for (day in 0..4) {
                grid[BaseCell(day, Shift.MORNING)] = mutableListOf()
                grid[BaseCell(day, Shift.EVENING)] = mutableListOf()
            }
            existingVisits
                .filterNot { it.isDeleted }
                .filter { it.cycleStartEpochDay == cycleStart.toEpochDay() }
                .sortedWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.date }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })
                .forEach { visit ->
                    val baseCell = inferBaseCell(visit) ?: return@forEach
                    grid[baseCell]?.add(visit)
                }
        }

        fun ensureCompanyRotation(company: Company): SchedulePlan {
            val current = visitsForCompany(company.id)
            val baseCell = company.lockedBaseCellOrNull() ?: lockedBaseCellForCompany(company.id) ?: chooseLeastLoadedBaseCell()
            val desired = (1..4).map { week -> createVisit(company.id, baseCell, week) }
            val desiredIds = desired.map { it.id }.toSet()

            // الإصلاح الجذري: أي زيارة لا تحمل الـ stable id الصحيح للأسبوع تُحذف.
            // هذا يزيل الزيارات القديمة/المكررة التي ظهرت أثناء انتقال Multi-Tenant، حتى لو كانت بنفس التاريخ والفترة.
            val deletes = current.filter { it.id !in desiredIds }
            val upserts = desired.filter { desiredVisit ->
                current.none { existing -> existing.id == desiredVisit.id && visitKey(existing) == visitKey(desiredVisit) }
            }

            deletes.forEach { removed ->
                inferBaseCell(removed)?.let { cell -> grid[cell]?.removeAll { it.id == removed.id } }
            }
            upserts.forEach { added -> grid[baseCell]?.add(added) }
            grid[baseCell]?.sortWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.slotIndex }.thenBy { it.companyId })

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
            .distinctBy { it.id }
            .sortedWith(compareBy<Visit> { it.weekOfCycle }.thenBy { it.date }.thenBy { it.shift.ordinal }.thenBy { it.slotIndex })

        private fun lockedBaseCellForCompany(companyId: String): BaseCell? = visitsForCompany(companyId)
            .firstOrNull { it.weekOfCycle == 1 }
            ?.let { inferBaseCell(it) }
            ?: visitsForCompany(companyId).firstOrNull()?.let { inferBaseCell(it) }

        private fun chooseLeastLoadedBaseCell(): BaseCell = (0..4)
            .flatMap { day -> listOf(BaseCell(day, Shift.MORNING), BaseCell(day, Shift.EVENING)) }
            .minWith(compareBy<BaseCell> { uniqueCompaniesInCell(it) }.thenBy { it.dayIndex }.thenBy { it.baseShift.ordinal })

        private fun uniqueCompaniesInCell(cell: BaseCell): Int = grid[cell].orEmpty().map { it.companyId }.distinct().size

        private fun inferBaseCell(visit: Visit): BaseCell? {
            val workingIndex = dateToWorkingIndex[visit.date] ?: return null
            val week = visit.weekOfCycle.coerceIn(1, 4)
            val dayWithinWeek = (workingIndex - 1) % 5 // 0..4
            val offset = week - 1
            val baseDay = Math.floorMod(dayWithinWeek - offset, 5)
            val baseShift = if (week == 2 || week == 4) visit.shift.inverted() else visit.shift
            return BaseCell(baseDay, baseShift)
        }

        private fun rotatedCell(baseCell: BaseCell, week: Int): RotatedCell {
            val offset = week - 1
            val day = (baseCell.dayIndex + offset) % 5
            val shift = if (week == 2 || week == 4) baseCell.baseShift.inverted() else baseCell.baseShift
            return RotatedCell(day, shift)
        }

        private fun createVisit(companyId: String, baseCell: BaseCell, week: Int): Visit {
            val rotated = rotatedCell(baseCell, week)
            val workingIndex = ((week - 1) * 5) + rotated.dayIndex + 1
            val date = workingDates[workingIndex - 1]
            val dayOfCycle = cycleCalculator.dayOfCycle(cycleStart, date)
            val slotIndex = legacySlotIndex(baseCell)
            return Visit(
                id = stableVisitId(companyId, cycleStart.toEpochDay(), week),
                companyId = companyId,
                cycleStartEpochDay = cycleStart.toEpochDay(),
                dayOfCycle = dayOfCycle,
                weekOfCycle = week,
                date = date,
                shift = rotated.shift,
                slotIndex = slotIndex,
            )
        }

        private fun legacySlotIndex(baseCell: BaseCell): Int {
            // Supabase deployments may still have the historical 1..10 slot_index constraint.
            // Open capacity is represented by allowing many companies to share the same display hint.
            val ordinal = uniqueCompaniesInCell(baseCell) + 1
            return ((ordinal - 1) % 10) + 1
        }

        private fun visitKey(visit: Visit): String = "${visit.companyId}|${visit.weekOfCycle}|${visit.date.toEpochDay()}|${visit.shift.name}"

        private fun stableVisitId(companyId: String, cycleStartEpochDay: Long, week: Int): String =
            UUID.nameUUIDFromBytes("$companyId-$cycleStartEpochDay-orthogonal-week-$week".toByteArray()).toString()
    }
}

data class SchedulePlan(
    val visitsToUpsert: List<Visit>,
    val visitsToSoftDelete: List<Visit>,
)

private data class BaseCell(
    val dayIndex: Int, // 0..4 = Sat..Wed in Week 1 master blueprint
    val baseShift: Shift,
)

private data class RotatedCell(
    val dayIndex: Int,
    val shift: Shift,
)

private fun Company.lockedBaseCellOrNull(): BaseCell? {
    val day = baseDayIndex ?: return null
    val shift = baseShift ?: return null
    return if (day in 0..4) BaseCell(day, shift) else null
}

private fun Shift.inverted(): Shift = when (this) {
    Shift.MORNING -> Shift.EVENING
    Shift.EVENING -> Shift.MORNING
}
