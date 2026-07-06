package com.borgpharmacy.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

enum class Shift(val displayName: String) {
    MORNING("الفترة الصباحية"),
    EVENING("الفترة المسائية");

    val arabicName: String
        get() = when (this) {
            MORNING -> "الفترة الصباحية"
            EVENING -> "الفترة المسائية"
        }
}

enum class Tier(val visitsPerCycle: Int, val label: String) {
    A(3, "Tier A"),
    B(2, "Tier B"),
    C(1, "Tier C"),
    UNRATED(0, "Unrated");

    companion object {
        fun fromString(value: String?): Tier = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNRATED
    }
}

enum class VisitStatus { SCHEDULED, COMPLETED, MISSED, CANCELLED }

enum class UserRole { ADMIN, PHARMACIST }

data class Company(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tier: Tier = Tier.UNRATED,
    val baseDayIndex: Int? = null, // immutable Week-1 coordinate: 0=Sat .. 4=Wed
    val baseShift: Shift? = null, // immutable Week-1 shift
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

data class Representative(
    val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val name: String,
    val phone: String = "+967",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

data class Visit(
    val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val cycleStartEpochDay: Long,
    val dayOfCycle: Int,
    val weekOfCycle: Int,
    val date: LocalDate,
    val shift: Shift,
    val slotIndex: Int,
    val status: VisitStatus = VisitStatus.SCHEDULED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
) {
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek
    val isDeleted: Boolean get() = deletedAt != null
}

data class PrintCount(
    val repId: String,
    val visitId: String,
    val count: Int,
)

data class UserAccount(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val displayName: String,
    val role: UserRole,
    val mustChangePasscode: Boolean = false,
    val isActive: Boolean = true,
)

data class CycleInfo(
    val baselineStart: LocalDate,
    val currentCycleStart: LocalDate,
    val currentCycleEnd: LocalDate,
    val today: LocalDate,
    val cycleNumber: Long,
    val dayOfCycle: Int,
    val weekOfCycle: Int,
) {
    val cycleName: String
        get() = currentCycleStart.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    val title: String
        get() = "الدورة الحالية: $cycleName - الأسبوع $weekOfCycle"

    val arabicTitle: String
        get() = "الدورة الحالية: $cycleName - الأسبوع $weekOfCycle"
}

data class VisitWithCompany(
    val visit: Visit,
    val company: Company,
)

data class DailySchedule(
    val date: LocalDate,
    val dayOfCycle: Int,
    val weekOfCycle: Int,
    val morning: List<VisitWithCompany>,
    val evening: List<VisitWithCompany>,
) {
    val dayName: String get() = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}

data class CompanyReportScore(
    val company: Company,
    val expectedVisits: Int,
    val completedVisits: Int,
    val scoreOutOf10: Double,
)

fun DayOfWeek.isBorgWorkingDay(): Boolean = when (this) {
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY -> true
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY -> false
}

fun DayOfWeek.borgShortName(): String = when (this) {
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
}

fun DayOfWeek.borgArabicName(): String = when (this) {
    DayOfWeek.SATURDAY -> "السبت"
    DayOfWeek.SUNDAY -> "الأحد"
    DayOfWeek.MONDAY -> "الإثنين"
    DayOfWeek.TUESDAY -> "الثلاثاء"
    DayOfWeek.WEDNESDAY -> "الأربعاء"
    DayOfWeek.THURSDAY -> "الخميس"
    DayOfWeek.FRIDAY -> "الجمعة"
}
