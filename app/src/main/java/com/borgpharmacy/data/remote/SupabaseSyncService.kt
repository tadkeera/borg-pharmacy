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

    suspend fun signInWithPassword(email: String, password: String): SupabaseAuthSession = withContext(Dispatchers.IO) {
        val response = postAuth(
            path = "token?grant_type=password",
            body = buildJsonObject {
                put("email", email.trim().lowercase())
                put("password", password)
            }
        )
        json.decodeFromString<AuthTokenResponseDto>(response).toDomain()
    }

    suspend fun fetchProfile(accessToken: String, userId: String): UserProfileRemoteDto? = withContext(Dispatchers.IO) {
        val encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8")
        val connection = (URL("${BuildConfig.SUPABASE_URL}/rest/v1/user_profiles?select=*&user_id=eq.$encodedUserId&limit=1").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Supabase profile fetch failed with HTTP $code: $response")
            }
            json.decodeFromString<List<UserProfileRemoteDto>>(response).firstOrNull()
        } finally {
            connection.disconnect()
        }
    }

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

    suspend fun adminCreateAuthUser(
        accessToken: String,
        email: String,
        password: String,
        displayName: String,
        role: String,
    ): CreatedAuthUserDto = withContext(Dispatchers.IO) {
        val response = postFunction(
            functionName = "admin-create-user",
            accessToken = accessToken,
            body = buildJsonObject {
                put("email", email.trim().lowercase())
                put("password", password)
                put("displayName", displayName.trim())
                put("role", if (role == "ADMIN") "ADMIN" else "PHARMACIST")
            }
        )
        json.decodeFromString<CreatedAuthUserDto>(response)
    }

    suspend fun pullAll(tenantId: String): RemoteSnapshot = withContext(Dispatchers.IO) {
    // 1. سحب الشركات الفعالة فقط والخاصة بالـ tenant الحالي
    val companies = client.from("companies")
        .select {
            filter {
                eq("tenant_id", tenantId)
                eq("is_deleted", false)
                exact("deleted_at", null)
            }
        }
        .decodeList<CompanyRemoteDto>()
        .map { it.toEntity() }

    // 2. سحب المندوبين الفعالين فقط
    val reps = client.from("representatives")
        .select {
            filter {
                eq("tenant_id", tenantId)
                eq("is_deleted", false)
                exact("deleted_at", null)
            }
        }
        .decodeList<RepresentativeRemoteDto>()
        .map { it.toEntity() }

    // 3. سحب الزيارات الفعالة فقط
    val visits = client.from("visits")
        .select {
            filter {
                eq("tenant_id", tenantId)
                eq("is_deleted", false)
                exact("deleted_at", null)
            }
        }
        .decodeList<VisitRemoteDto>()
        .map { it.toEntity() }

    val users = runCatching { pullUsers(tenantId) }.getOrDefault(emptyList())
    RemoteSnapshot(companies, reps, visits, users)
}

private suspend fun pullUsers(tenantId: String): List<UserEntity> {
    val response = postRpc(
        functionName = "borg_pull_users",
        body = buildJsonObject { 
            put("p_token", BuildConfig.SUPABASE_SYNC_TOKEN)
            put("p_tenant_id", tenantId)
        },
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

    private suspend fun postAuth(path: String, body: JsonObject): String = withContext(Dispatchers.IO) {
        val connection = (URL("${BuildConfig.SUPABASE_URL}/auth/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            val bytes = json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Supabase Auth request failed with HTTP $code: $response")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun postFunction(functionName: String, accessToken: String, body: JsonObject): String = withContext(Dispatchers.IO) {
        val connection = (URL("${BuildConfig.SUPABASE_URL}/functions/v1/$functionName").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            val bytes = json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Supabase Function $functionName failed with HTTP $code: $response")
            }
            response
        } finally {
            connection.disconnect()
        }
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

data class SupabaseAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val userId: String,
    val email: String,
)

@Serializable
data class AuthTokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 0,
    val user: AuthUserDto,
) {
    fun toDomain(): SupabaseAuthSession = SupabaseAuthSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = expiresIn,
        userId = user.id,
        email = user.email.orEmpty(),
    )
}

@Serializable
data class AuthUserDto(
    val id: String,
    val email: String? = null,
)

@Serializable
data class UserProfileRemoteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("display_name") val displayName: String = "",
    val role: String = "PHARMACIST",
    val active: Boolean = true,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
)

@Serializable
data class CreatedAuthUserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val tenantId: String,
)

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

private fun CompanyRemoteDto.toEntity() = CompanyEntity(id = id, tenantId = tenantId, name = name, tier = tier, baseDayIndex = baseDayIndex, baseShift = baseShift, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false, syncStatus = syncStatus, isDeleted = isDeleted || deletedAt != null)
private fun RepresentativeRemoteDto.toEntity() = RepresentativeEntity(id = id, tenantId = tenantId, companyId = companyId, name = name, phone = phone, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false, syncStatus = syncStatus, isDeleted = isDeleted || deletedAt != null)
private fun VisitRemoteDto.toEntity() = VisitEntity(id = id, tenantId = tenantId, companyId = companyId, cycleStartEpochDay = cycleStartEpochDay, dayOfCycle = dayOfCycle, weekOfCycle = weekOfCycle, dateEpochDay = dateEpochDay, shift = shift, slotIndex = slotIndex, status = status, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, dirty = false, syncStatus = syncStatus, isDeleted = isDeleted || deletedAt != null)
private fun UserRemoteDto.toEntity() = UserEntity(id = id, tenantId = tenantId, username = username, displayName = displayName, role = role, passcodeHash = passcodeHash, mustChangePasscode = mustChangePasscode, active = active, createdAt = createdAt, updatedAt = updatedAt, syncStatus = syncStatus, isDeleted = isDeleted)
