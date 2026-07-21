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

const val DEFAULT_TENANT_ID: String = "00000000-0000-0000-0000-000000000000"

enum class SyncStatus { PENDING, SYNCED, FAILED }

interface TenantScopedEntity {
    val tenantId: String
    val updatedAt: Long
    val syncStatus: String
    val isDeleted: Boolean
}

@Entity(
    tableName = "companies",
    indices = [Index("tenantId"), Index(value = ["tenantId", "name"])],
)
data class CompanyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    override val tenantId: String = DEFAULT_TENANT_ID,
    val name: String,
    val tier: String = Tier.UNRATED.name,
    val baseDayIndex: Int? = null,
    val baseShift: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
    override val syncStatus: String = SyncStatus.PENDING.name,
    override val isDeleted: Boolean = deletedAt != null,
) : TenantScopedEntity

@Entity(
    tableName = "representatives",
    foreignKeys = [ForeignKey(
        entity = CompanyEntity::class,
        parentColumns = ["id"],
        childColumns = ["companyId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("tenantId"), Index("companyId"), Index("phone"), Index(value = ["tenantId", "phone"])],
)
data class RepresentativeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    override val tenantId: String = DEFAULT_TENANT_ID,
    val companyId: String,
    val name: String,
    val phone: String = "+967",
    val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
    override val syncStatus: String = SyncStatus.PENDING.name,
    override val isDeleted: Boolean = deletedAt != null,
) : TenantScopedEntity

@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(
        entity = CompanyEntity::class,
        parentColumns = ["id"],
        childColumns = ["companyId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("tenantId"), Index("companyId"), Index("cycleStartEpochDay"), Index("dateEpochDay"), Index("shift"), Index(value = ["tenantId", "updatedAt"])],
)
data class VisitEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    override val tenantId: String = DEFAULT_TENANT_ID,
    val companyId: String,
    val cycleStartEpochDay: Long,
    val dayOfCycle: Int,
    val weekOfCycle: Int,
    val dateEpochDay: Long,
    val shift: String,
    val slotIndex: Int,
    val status: String = VisitStatus.SCHEDULED.name,
    val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
    override val syncStatus: String = SyncStatus.PENDING.name,
    override val isDeleted: Boolean = deletedAt != null,
) : TenantScopedEntity

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
    indices = [Index("tenantId"), Index("repId"), Index("visitId")],
)
data class PrintLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    override val tenantId: String = DEFAULT_TENANT_ID,
    val repId: String,
    val visitId: String,
    val printedAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val syncStatus: String = SyncStatus.PENDING.name,
    override val isDeleted: Boolean = false,
) : TenantScopedEntity

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true), Index("tenantId")])
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    override val tenantId: String = DEFAULT_TENANT_ID,
    val username: String,
    val displayName: String,
    val role: String,
    val passcodeHash: String,
    val mustChangePasscode: Boolean = false,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val syncStatus: String = SyncStatus.PENDING.name,
    override val isDeleted: Boolean = !active,
) : TenantScopedEntity

@Entity(tableName = "app_settings", indices = [Index("tenantId")])
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    override val tenantId: String = DEFAULT_TENANT_ID,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val syncStatus: String = SyncStatus.PENDING.name,
    override val isDeleted: Boolean = false,
) : TenantScopedEntity

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
    deletedAt = deletedAt ?: if (isDeleted) updatedAt else null,
)

fun Company.toEntity(dirty: Boolean = true, tenantId: String = DEFAULT_TENANT_ID): CompanyEntity = CompanyEntity(
    id = id,
    tenantId = tenantId,
    name = name.cleanCompanyName(),
    tier = tier.name,
    baseDayIndex = baseDayIndex,
    baseShift = baseShift?.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = dirty,
    syncStatus = if (dirty) SyncStatus.PENDING.name else SyncStatus.SYNCED.name,
    isDeleted = deletedAt != null,
)

fun RepresentativeEntity.toDomain(): Representative = Representative(
    id = id,
    companyId = companyId,
    name = name,
    phone = phone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt ?: if (isDeleted) updatedAt else null,
)

fun Representative.toEntity(dirty: Boolean = true, tenantId: String = DEFAULT_TENANT_ID): RepresentativeEntity = RepresentativeEntity(
    id = id,
    tenantId = tenantId,
    companyId = companyId,
    name = name.trim(),
    phone = phone.ifBlank { "+967" },
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = dirty,
    syncStatus = if (dirty) SyncStatus.PENDING.name else SyncStatus.SYNCED.name,
    isDeleted = deletedAt != null,
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
    deletedAt = deletedAt ?: if (isDeleted) updatedAt else null,
)

fun Visit.toEntity(dirty: Boolean = true, tenantId: String = DEFAULT_TENANT_ID): VisitEntity = VisitEntity(
    id = id,
    tenantId = tenantId,
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
    syncStatus = if (dirty) SyncStatus.PENDING.name else SyncStatus.SYNCED.name,
    isDeleted = deletedAt != null,
)

fun UserEntity.toDomain(): UserAccount = UserAccount(
    id = id,
    username = username,
    displayName = displayName,
    role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.PHARMACIST),
    mustChangePasscode = mustChangePasscode,
    isActive = active && !isDeleted,
)

private fun String.cleanCompanyName(): String = trim()
    .trim('"', '\'', '“', '”')
    .trim()
