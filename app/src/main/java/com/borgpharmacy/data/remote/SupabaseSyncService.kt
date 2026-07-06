package com.borgpharmacy.data.remote

import com.borgpharmacy.data.local.CompanyEntity
import com.borgpharmacy.data.local.RepresentativeEntity
import com.borgpharmacy.data.local.VisitEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Thin cloud sync adapter. Room remains the source of truth; this class pushes dirty local rows to
 * Supabase and can later be extended with Realtime subscriptions for live updates.
 */
class SupabaseSyncService(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    suspend fun pushCompanies(companies: List<CompanyEntity>) {
        if (companies.isEmpty()) return
        client.from("companies").upsert(companies.map { it.toRemote() })
    }

    suspend fun pushRepresentatives(representatives: List<RepresentativeEntity>) {
        if (representatives.isEmpty()) return
        client.from("representatives").upsert(representatives.map { it.toRemote() })
    }

    suspend fun pushVisits(visits: List<VisitEntity>) {
        if (visits.isEmpty()) return
        client.from("visits").upsert(visits.map { it.toRemote() })
    }

    suspend fun pullAll(): RemoteSnapshot {
        val companies = client.from("companies").select().decodeList<CompanyRemoteDto>().map { it.toEntity() }
        val reps = client.from("representatives").select().decodeList<RepresentativeRemoteDto>().map { it.toEntity() }
        val visits = client.from("visits").select().decodeList<VisitRemoteDto>().map { it.toEntity() }
        return RemoteSnapshot(companies, reps, visits)
    }
}

data class RemoteSnapshot(
    val companies: List<CompanyEntity>,
    val representatives: List<RepresentativeEntity>,
    val visits: List<VisitEntity>,
)

@Serializable
data class CompanyRemoteDto(
    val id: String,
    val name: String,
    val tier: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
)

@Serializable
data class RepresentativeRemoteDto(
    val id: String,
    @SerialName("company_id") val companyId: String,
    val name: String,
    val phone: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
)

@Serializable
data class VisitRemoteDto(
    val id: String,
    @SerialName("company_id") val companyId: String,
    @SerialName("cycle_start_epoch_day") val cycleStartEpochDay: Long,
    @SerialName("day_of_cycle") val dayOfCycle: Int,
    @SerialName("week_of_cycle") val weekOfCycle: Int,
    @SerialName("date_epoch_day") val dateEpochDay: Long,
    val shift: String,
    @SerialName("slot_index") val slotIndex: Int,
    val status: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
)

private fun CompanyEntity.toRemote() = CompanyRemoteDto(id, name, tier, createdAt, updatedAt, deletedAt)
private fun RepresentativeEntity.toRemote() = RepresentativeRemoteDto(id, companyId, name, phone, createdAt, updatedAt, deletedAt)
private fun VisitEntity.toRemote() = VisitRemoteDto(id, companyId, cycleStartEpochDay, dayOfCycle, weekOfCycle, dateEpochDay, shift, slotIndex, status, createdAt, updatedAt, deletedAt)

private fun CompanyRemoteDto.toEntity() = CompanyEntity(id = id, name = name, tier = tier, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false)
private fun RepresentativeRemoteDto.toEntity() = RepresentativeEntity(id = id, companyId = companyId, name = name, phone = phone, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false)
private fun VisitRemoteDto.toEntity() = VisitEntity(id = id, companyId = companyId, cycleStartEpochDay = cycleStartEpochDay, dayOfCycle = dayOfCycle, weekOfCycle = weekOfCycle, dateEpochDay = dateEpochDay, shift = shift, slotIndex = slotIndex, status = status, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false)
