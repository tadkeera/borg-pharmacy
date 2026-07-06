package com.borgpharmacy.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.borgpharmacy.domain.Company
import com.borgpharmacy.domain.Representative
import com.borgpharmacy.domain.Shift
import com.borgpharmacy.domain.Tier
import com.borgpharmacy.domain.UserAccount
import com.borgpharmacy.domain.UserRole
import com.borgpharmacy.domain.Visit
import com.borgpharmacy.domain.VisitStatus
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tier: String = Tier.UNRATED.name,
    val baseDayIndex: Int? = null,
    val baseShift: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "representatives",
    foreignKeys = [ForeignKey(
        entity = CompanyEntity::class,
        parentColumns = ["id"],
        childColumns = ["companyId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("companyId"), Index("phone")],
)
data class RepresentativeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val name: String,
    val phone: String = "+967",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(
        entity = CompanyEntity::class,
        parentColumns = ["id"],
        childColumns = ["companyId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("companyId"), Index("cycleStartEpochDay"), Index("dateEpochDay"), Index("shift")],
)
data class VisitEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val cycleStartEpochDay: Long,
    val dayOfCycle: Int,
    val weekOfCycle: Int,
    val dateEpochDay: Long,
    val shift: String,
    val slotIndex: Int,
    val status: String = VisitStatus.SCHEDULED.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "print_logs",
    foreignKeys = [
        ForeignKey(
            entity = RepresentativeEntity::class,
            parentColumns = ["id"],
            childColumns = ["repId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("repId"), Index("visitId")],
)
data class PrintLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val repId: String,
    val visitId: String,
    val printedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val username: String,
    val displayName: String,
    val role: String,
    val passcodeHash: String,
    val mustChangePasscode: Boolean = false,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class PrintCountTuple(
    val repId: String,
    val visitId: String,
    val count: Int,
)

data class TierCountTuple(
    val tier: String,
    val count: Int,
)

fun CompanyEntity.toDomain(): Company = Company(
    id = id,
    name = name.cleanCompanyName(),
    tier = Tier.fromString(tier),
    baseDayIndex = baseDayIndex,
    baseShift = baseShift?.let { runCatching { Shift.valueOf(it) }.getOrNull() },
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Company.toEntity(dirty: Boolean = true): CompanyEntity = CompanyEntity(
    id = id,
    name = name.cleanCompanyName(),
    tier = tier.name,
    baseDayIndex = baseDayIndex,
    baseShift = baseShift?.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = dirty,
)

fun RepresentativeEntity.toDomain(): Representative = Representative(
    id = id,
    companyId = companyId,
    name = name,
    phone = phone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Representative.toEntity(dirty: Boolean = true): RepresentativeEntity = RepresentativeEntity(
    id = id,
    companyId = companyId,
    name = name.trim(),
    phone = phone.ifBlank { "+967" },
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = dirty,
)

fun VisitEntity.toDomain(): Visit = Visit(
    id = id,
    companyId = companyId,
    cycleStartEpochDay = cycleStartEpochDay,
    dayOfCycle = dayOfCycle,
    weekOfCycle = weekOfCycle,
    date = LocalDate.ofEpochDay(dateEpochDay),
    shift = Shift.valueOf(shift),
    slotIndex = slotIndex,
    status = VisitStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Visit.toEntity(dirty: Boolean = true): VisitEntity = VisitEntity(
    id = id,
    companyId = companyId,
    cycleStartEpochDay = cycleStartEpochDay,
    dayOfCycle = dayOfCycle,
    weekOfCycle = weekOfCycle,
    dateEpochDay = date.toEpochDay(),
    shift = shift.name,
    slotIndex = slotIndex,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = dirty,
)

fun UserEntity.toDomain(): UserAccount = UserAccount(
    id = id,
    username = username,
    displayName = displayName,
    role = UserRole.valueOf(role),
    mustChangePasscode = mustChangePasscode,
    isActive = active,
)

private fun String.cleanCompanyName(): String = trim()
    .trim('"', '\'', '“', '”')
    .trim()
