package com.borgpharmacy.data.remote

import com.borgpharmacy.BuildConfig
import com.borgpharmacy.data.local.CompanyEntity
import com.borgpharmacy.data.local.RepresentativeEntity
import com.borgpharmacy.data.local.UserEntity
import com.borgpharmacy.data.local.VisitEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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
 * الكتابة تمر عبر RPC محددة ومحمية بتوكن مزامنة خاص بالتطبيق، بينما تبقى القراءة العامة للجداول غير الحساسة فقط.
 * جدول المستخدمين لا يُقرأ مباشرة بمفتاح anon، بل يُسحب عبر RPC محمية بنفس توكن المزامنة.
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

    suspend fun pushUsers(users: List<UserEntity>) {
        if (users.isEmpty()) return
        postSyncRpc("borg_sync_users", json.encodeToJsonElement(users.map { it.toRemote() }))
    }

    suspend fun loginUser(username: String, passcodeHash: String): UserEntity? {
        val response = postRpc(
            functionName = "borg_login_user",
            body = buildJsonObject {
                put("p_token", BuildConfig.SUPABASE_SYNC_TOKEN)
                put("p_username", username.trim().lowercase())
                put("p_passcode_hash", passcodeHash)
            },
            preferReturnMinimal = false,
        )
        return json.decodeFromString<List<UserRemoteDto>>(response).firstOrNull()?.toEntity()
    }

    suspend fun pullAll(): RemoteSnapshot {
        val companies = client.from("companies").select().decodeList<CompanyRemoteDto>().map { it.toEntity() }
        val reps = client.from("representatives").select().decodeList<RepresentativeRemoteDto>().map { it.toEntity() }
        val visits = client.from("visits").select().decodeList<VisitRemoteDto>().map { it.toEntity() }
        val users = runCatching { pullUsers() }.getOrDefault(emptyList())
        return RemoteSnapshot(companies, reps, visits, users)
    }

    private suspend fun pullUsers(): List<UserEntity> {
        val response = postRpc(
            functionName = "borg_pull_users",
            body = buildJsonObject { put("p_token", BuildConfig.SUPABASE_SYNC_TOKEN) },
            preferReturnMinimal = false,
        )
        return json.decodeFromString<List<UserRemoteDto>>(response).map { it.toEntity() }
    }

    private suspend fun postSyncRpc(functionName: String, rows: JsonElement) {
        postRpc(
            functionName = functionName,
            body = buildJsonObject {
                put("p_token", BuildConfig.SUPABASE_SYNC_TOKEN)
                put("p_rows", rows)
            },
            preferReturnMinimal = true,
        )
    }

    private suspend fun postRpc(functionName: String, body: JsonObject, preferReturnMinimal: Boolean): String = withContext(Dispatchers.IO) {
        val connection = (URL("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/$functionName").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (preferReturnMinimal) setRequestProperty("Prefer", "return=minimal")
        }

        try {
            val bytes = json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Supabase RPC $functionName failed with HTTP $code: $response")
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}

data class RemoteSnapshot(
    val companies: List<CompanyEntity>,
    val representatives: List<RepresentativeEntity>,
    val visits: List<VisitEntity>,
    val users: List<UserEntity>,
)

@Serializable
data class CompanyRemoteDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String = com.borgpharmacy.data.local.DEFAULT_TENANT_ID,
    val name: String,
    val tier: String,
    @SerialName("base_day_index") val baseDayIndex: Int? = null,
    @SerialName("base_shift") val baseShift: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("is_deleted") val isDeleted: Boolean = deletedAt != null,
)

@Serializable
data class RepresentativeRemoteDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String = com.borgpharmacy.data.local.DEFAULT_TENANT_ID,
    @SerialName("company_id") val companyId: String,
    val name: String,
    val phone: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("is_deleted") val isDeleted: Boolean = deletedAt != null,
)

@Serializable
data class VisitRemoteDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String = com.borgpharmacy.data.local.DEFAULT_TENANT_ID,
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
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("is_deleted") val isDeleted: Boolean = deletedAt != null,
)

@Serializable
data class UserRemoteDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String = com.borgpharmacy.data.local.DEFAULT_TENANT_ID,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    @SerialName("passcode_hash") val passcodeHash: String,
    @SerialName("must_change_passcode") val mustChangePasscode: Boolean = false,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("is_deleted") val isDeleted: Boolean = !active,
)

private fun CompanyEntity.toRemote() = CompanyRemoteDto(
    id = id,
    tenantId = tenantId,
    name = name,
    tier = tier,
    baseDayIndex = baseDayIndex,
    baseShift = baseShift,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    isDeleted = isDeleted,
)
private fun RepresentativeEntity.toRemote() = RepresentativeRemoteDto(id, tenantId, companyId, name, phone, createdAt, updatedAt, deletedAt, syncStatus, isDeleted)
private fun VisitEntity.toRemote() = VisitRemoteDto(id, tenantId, companyId, cycleStartEpochDay, dayOfCycle, weekOfCycle, dateEpochDay, shift, slotIndex, status, createdAt, updatedAt, deletedAt, syncStatus, isDeleted)
private fun UserEntity.toRemote() = UserRemoteDto(id, tenantId, username, displayName, role, passcodeHash, mustChangePasscode, active, createdAt, updatedAt, syncStatus, isDeleted)

private fun CompanyRemoteDto.toEntity() = CompanyEntity(id = id, tenantId = tenantId, name = name, tier = tier, baseDayIndex = baseDayIndex, baseShift = baseShift, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false, syncStatus = syncStatus, isDeleted = isDeleted)
private fun RepresentativeRemoteDto.toEntity() = RepresentativeEntity(id = id, tenantId = tenantId, companyId = companyId, name = name, phone = phone, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false, syncStatus = syncStatus, isDeleted = isDeleted)
private fun VisitRemoteDto.toEntity() = VisitEntity(id = id, tenantId = tenantId, companyId = companyId, cycleStartEpochDay = cycleStartEpochDay, dayOfCycle = dayOfCycle, weekOfCycle = weekOfCycle, dateEpochDay = dateEpochDay, shift = shift, slotIndex = slotIndex, status = status, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false, syncStatus = syncStatus, isDeleted = isDeleted)
private fun UserRemoteDto.toEntity() = UserEntity(id = id, tenantId = tenantId, username = username, displayName = displayName, role = role, passcodeHash = passcodeHash, mustChangePasscode = mustChangePasscode, active = active, createdAt = createdAt, updatedAt = updatedAt, syncStatus = syncStatus, isDeleted = isDeleted)
