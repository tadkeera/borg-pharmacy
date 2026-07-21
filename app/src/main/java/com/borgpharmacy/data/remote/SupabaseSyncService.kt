package com.borgpharmacy.data.remote

import com.borgpharmacy.BuildConfig
import com.borgpharmacy.data.local.CompanyEntity
import com.borgpharmacy.data.local.RepresentativeEntity
import com.borgpharmacy.data.local.VisitEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin cloud sync adapter. Room remains the source of truth.
 *
 * مهم أمنيًا: عمليات الكتابة لا تتم مباشرة على الجداول بمفتاح anon.
 * الكتابة تمر عبر RPC محددة ومحمية بتوكن مزامنة خاص بالتطبيق، بينما تبقى القراءة عبر PostgREST.
 */
class SupabaseSyncService(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun pushCompanies(companies: List<CompanyEntity>) {
        if (companies.isEmpty()) return
        postSyncRpc("borg_sync_companies", json.encodeToJsonElement(companies.map { it.toRemote() }))
    }

    suspend fun pushRepresentatives(representatives: List<RepresentativeEntity>) {
        if (representatives.isEmpty()) return
        postSyncRpc("borg_sync_representatives", json.encodeToJsonElement(representatives.map { it.toRemote() }))
    }

    suspend fun pushVisits(visits: List<VisitEntity>) {
        if (visits.isEmpty()) return
        postSyncRpc("borg_sync_visits", json.encodeToJsonElement(visits.map { it.toRemote() }))
    }

    suspend fun pullAll(): RemoteSnapshot {
        val companies = client.from("companies").select().decodeList<CompanyRemoteDto>().map { it.toEntity() }
        val reps = client.from("representatives").select().decodeList<RepresentativeRemoteDto>().map { it.toEntity() }
        val visits = client.from("visits").select().decodeList<VisitRemoteDto>().map { it.toEntity() }
        return RemoteSnapshot(companies, reps, visits)
    }

    private suspend fun postSyncRpc(functionName: String, rows: JsonElement) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_token", BuildConfig.SUPABASE_SYNC_TOKEN)
            put("p_rows", rows)
        }
        val connection = (URL("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/$functionName").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Prefer", "return=minimal")
        }

        try {
            val bytes = json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Supabase RPC $functionName failed with HTTP $code: $error")
            }
        } finally {
            connection.disconnect()
        }
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
    @SerialName("base_day_index") val baseDayIndex: Int? = null,
    @SerialName("base_shift") val baseShift: String? = null,
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

private fun CompanyEntity.toRemote() = CompanyRemoteDto(
    id = id,
    name = name,
    tier = tier,
    baseDayIndex = baseDayIndex,
    baseShift = baseShift,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
private fun RepresentativeEntity.toRemote() = RepresentativeRemoteDto(id, companyId, name, phone, createdAt, updatedAt, deletedAt)
private fun VisitEntity.toRemote() = VisitRemoteDto(id, companyId, cycleStartEpochDay, dayOfCycle, weekOfCycle, dateEpochDay, shift, slotIndex, status, createdAt, updatedAt, deletedAt)

private fun CompanyRemoteDto.toEntity() = CompanyEntity(id = id, name = name, tier = tier, baseDayIndex = baseDayIndex, baseShift = baseShift, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false)
private fun RepresentativeRemoteDto.toEntity() = RepresentativeEntity(id = id, companyId = companyId, name = name, phone = phone, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false)
private fun VisitRemoteDto.toEntity() = VisitEntity(id = id, companyId = companyId, cycleStartEpochDay = cycleStartEpochDay, dayOfCycle = dayOfCycle, weekOfCycle = weekOfCycle, dateEpochDay = dateEpochDay, shift = shift, slotIndex = slotIndex, status = status, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false)
