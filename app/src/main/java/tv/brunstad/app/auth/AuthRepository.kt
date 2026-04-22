package tv.brunstad.app.auth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val DOMAIN = "https://login.bcc.no"
private const val CLIENT_ID = "CU6aNYSKaD6vpgFZvLJ9gvGAFnKRlpir"
private const val AUDIENCE = "api.bcc.no"
private const val SCOPE = "openid profile offline_access church country email"

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresIn: Int,
    val interval: Int
)

sealed class PollResult {
    data class Success(val accessToken: String) : PollResult()
    object Pending : PollResult()
    data class Error(val message: String) : PollResult()
}

private sealed class RefreshResult {
    data class Success(val accessToken: String) : RefreshResult()
    object NetworkError : RefreshResult()
    object AuthFailed : RefreshResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    private val tokenStore: TokenStore,
    private val profileStore: ProfileStore
) {

    private val _authExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when a token refresh fails due to an auth error (not a network error). */
    val authExpiredEvent: SharedFlow<Unit> = _authExpiredEvent

    init {
        migrateIfNeeded()
    }

    /** One-time migration: promote legacy single-account tokens to the profile-keyed format. */
    private fun migrateIfNeeded() {
        if (profileStore.hasProfiles()) return
        val legacyTokens = tokenStore.loadLegacy() ?: return
        val tokenToParse = legacyTokens.idToken.takeIf { it.isNotEmpty() } ?: legacyTokens.accessToken
        val userId = extractClaimFromJwt(tokenToParse, "sub") ?: return
        val displayName = extractClaimFromJwt(tokenToParse, "name")
            ?: userId.substringAfter("|").ifEmpty { "User" }
        profileStore.saveProfile(
            Profile(
                userId = userId,
                displayName = displayName,
                initials = generateInitials(displayName),
                lastUsed = System.currentTimeMillis()
            )
        )
        profileStore.activeProfileId = userId
        tokenStore.save(legacyTokens, userId)
        tokenStore.clearLegacy()
    }

    suspend fun startDeviceFlow(): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPE)
                .add("audience", AUDIENCE)
                .build()
            val request = Request.Builder()
                .url("$DOMAIN/oauth/device/code")
                .post(body)
                .build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.use { it.body!!.string() }
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                error("${json.optString("error")}: ${json.optString("error_description")}")
            }
            DeviceCodeResponse(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUri = json.getString("verification_uri"),
                verificationUriComplete = json.optString(
                    "verification_uri_complete",
                    json.getString("verification_uri")
                ),
                expiresIn = json.getInt("expires_in"),
                interval = json.getInt("interval")
            )
        }
    }

    fun pollForToken(deviceCode: String, intervalSeconds: Int): Flow<PollResult> = flow {
        var pollInterval = intervalSeconds.toLong()
        while (true) {
            delay(pollInterval * 1_000)
            val body = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("device_code", deviceCode)
                .add("client_id", CLIENT_ID)
                .build()
            val request = Request.Builder()
                .url("$DOMAIN/oauth/token")
                .post(body)
                .build()
            val responseBody = httpClient.newCall(request).execute().use { it.body!!.string() }
            val json = JSONObject(responseBody)
            when {
                json.has("access_token") -> {
                    val accessToken = json.getString("access_token")
                    val idToken = json.optString("id_token", "")
                    val refreshToken = json.optString("refresh_token", "")
                    val tokens = StoredTokens(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = System.currentTimeMillis() + json.getInt("expires_in") * 1_000L,
                        idToken = idToken
                    )
                    val tokenToParse = idToken.takeIf { it.isNotEmpty() } ?: accessToken
                    val userId = extractClaimFromJwt(tokenToParse, "sub")
                        ?: "unknown_${System.currentTimeMillis()}"
                    val displayName = extractClaimFromJwt(tokenToParse, "name")
                        ?: userId.substringAfter("|").ifEmpty { "User" }
                    profileStore.saveProfile(
                        Profile(
                            userId = userId,
                            displayName = displayName,
                            initials = generateInitials(displayName),
                            lastUsed = System.currentTimeMillis()
                        )
                    )
                    profileStore.activeProfileId = userId
                    tokenStore.save(tokens, userId)
                    emit(PollResult.Success(accessToken))
                    return@flow
                }
                json.optString("error") == "slow_down" -> {
                    pollInterval += 5
                    emit(PollResult.Pending)
                }
                json.optString("error") == "authorization_pending" -> emit(PollResult.Pending)
                else -> {
                    emit(PollResult.Error(json.optString("error_description", "Login failed")))
                    return@flow
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getValidAccessToken(): String? {
        val userId = profileStore.activeProfileId ?: return null
        val tokens = tokenStore.load(userId) ?: return null
        if (System.currentTimeMillis() < tokens.expiresAt - 60_000) {
            return tokens.accessToken
        }
        return when (val result = tryRefreshToken(userId, tokens.refreshToken)) {
            is RefreshResult.Success -> result.accessToken
            // Network error: use the stale token and let the API decide. This avoids
            // stripping the auth header on a transient network failure (e.g. emulator wake).
            RefreshResult.NetworkError -> tokens.accessToken
            // Auth error: refresh token is genuinely invalid. Profile removed, login required.
            RefreshResult.AuthFailed -> null
        }
    }

    private suspend fun tryRefreshToken(userId: String, refreshToken: String): RefreshResult =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("client_id", CLIENT_ID)
                    .add("refresh_token", refreshToken)
                    .build()
                val request = Request.Builder()
                    .url("$DOMAIN/oauth/token")
                    .post(body)
                    .build()
                val responseBody = httpClient.newCall(request).execute().use { it.body!!.string() }
                val json = JSONObject(responseBody)
                if (json.has("access_token")) {
                    tokenStore.save(
                        StoredTokens(
                            accessToken = json.getString("access_token"),
                            refreshToken = json.optString("refresh_token", refreshToken),
                            expiresAt = System.currentTimeMillis() + json.getInt("expires_in") * 1_000L
                        ),
                        userId
                    )
                    RefreshResult.Success(json.getString("access_token"))
                } else {
                    // Auth0 returned an error (e.g. invalid_grant — refresh token expired/revoked)
                    tokenStore.clear(userId)
                    profileStore.removeProfile(userId)
                    _authExpiredEvent.tryEmit(Unit)
                    RefreshResult.AuthFailed
                }
            } catch (_: Exception) {
                // Network or I/O error — treat as transient, don't clear tokens
                RefreshResult.NetworkError
            }
        }

    fun isLoggedIn(): Boolean {
        val userId = profileStore.activeProfileId ?: return false
        return tokenStore.load(userId) != null
    }

    /** Removes the active profile and its tokens. ProfileStore auto-selects the next MRU profile. */
    fun removeCurrentProfile() {
        val userId = profileStore.activeProfileId ?: return
        tokenStore.clear(userId)
        profileStore.removeProfile(userId)
    }

    fun hasMultipleProfiles(): Boolean = profileStore.getProfiles().size > 1

    fun getLocaleFromToken(): String? {
        val userId = profileStore.activeProfileId ?: return null
        val idToken = tokenStore.load(userId)?.idToken?.takeIf { it.isNotEmpty() } ?: return null
        return extractClaimFromJwt(idToken, "locale")
    }

    suspend fun fetchAgeGroup(): String? = withContext(Dispatchers.IO) {
        val token = getValidAccessToken() ?: return@withContext null
        try {
            val request = Request.Builder()
                .url("$DOMAIN/userinfo")
                .header("Authorization", "Bearer $token")
                .build()
            val body = httpClient.newCall(request).execute().use { it.body?.string() } ?: return@withContext null
            val json = JSONObject(body)
            val birthdate = json.optString("birthdate").ifEmpty { null } ?: return@withContext null
            val age = calculateAge(birthdate) ?: return@withContext null
            getAgeGroupLabel(age)
        } catch (_: Exception) { null }
    }

    private fun calculateAge(birthdate: String): Int? {
        return try {
            val parts = birthdate.split("-")
            if (parts.size < 3) return null
            val birthYear = parts[0].toInt()
            val birthMonth = parts[1].toInt()
            val birthDay = parts[2].substringBefore("T").toInt()
            val now = java.util.Calendar.getInstance()
            var age = now.get(java.util.Calendar.YEAR) - birthYear
            val monthNow = now.get(java.util.Calendar.MONTH) + 1
            val dayNow = now.get(java.util.Calendar.DAY_OF_MONTH)
            if (monthNow < birthMonth || (monthNow == birthMonth && dayNow < birthDay)) age--
            if (age < 0) null else age
        } catch (_: Exception) { null }
    }

    private fun getAgeGroupLabel(age: Int): String {
        return when {
            age <= 9 -> "< 10"
            age <= 12 -> "10 - 12"
            age <= 18 -> "13 - 18"
            age <= 25 -> "19 - 25"
            age <= 36 -> "26 - 36"
            age <= 50 -> "37 - 50"
            age <= 64 -> "51 - 64"
            else -> "65+"
        }
    }

    private fun extractClaimFromJwt(token: String, claim: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            JSONObject(payloadJson).optString(claim).ifEmpty { null }
        } catch (_: Exception) { null }
    }
}

private fun generateInitials(displayName: String): String {
    val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "?"
    }
}
