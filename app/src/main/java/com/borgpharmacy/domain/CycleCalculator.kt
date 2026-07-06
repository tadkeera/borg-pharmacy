package com.borgpharmacy.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit


class CycleCalculator(
    private val cycleLengthDays: Int = 28,
) {
    fun currentCycle(baselineStart: LocalDate, today: LocalDate = LocalDate.now()): CycleInfo {
        val rawDays = ChronoUnit.DAYS.between(baselineStart, today)
        val cycleOffset = Math.floorDiv(rawDays, cycleLengthDays.toLong())
        val normalizedOffset = rawDays - (cycleOffset * cycleLengthDays)
        val currentStart = baselineStart.plusDays(cycleOffset * cycleLengthDays)
        val dayOfCycle = normalizedOffset.toInt() + 1
        val week = ((dayOfCycle - 1) / 7) + 1
        return CycleInfo(
            baselineStart = baselineStart,
            currentCycleStart = currentStart,
            currentCycleEnd = currentStart.plusDays(cycleLengthDays - 1L),
            today = today,
            cycleNumber = cycleOffset + 1,
            dayOfCycle = dayOfCycle,
            weekOfCycle = week,
        )
    }

    fun weekOfCycle(dayOfCycle: Int): Int = ((dayOfCycle - 1) / 7) + 1

    fun dayOfCycle(cycleStart: LocalDate, date: LocalDate): Int =
        ChronoUnit.DAYS.between(cycleStart, date).toInt() + 1

    fun datesInCycle(cycleStart: LocalDate): List<LocalDate> =
        (0 until cycleLengthDays).map { cycleStart.plusDays(it.toLong()) }

    fun workingDatesInCycle(cycleStart: LocalDate): List<LocalDate> =
        datesInCycle(cycleStart).filter { it.dayOfWeek.isBorgWorkingDay() }
}
