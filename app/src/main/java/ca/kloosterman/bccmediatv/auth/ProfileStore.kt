package tv.brunstad.app.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class Profile(
    val userId: String,
    val displayName: String,
    val initials: String,
    val lastUsed: Long = 0L
)

@Singleton
class ProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PREFS_NAME = "bccmedia_profiles"
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_PROFILES = "profiles"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var activeProfileId: String?
        get() = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        set(value) { prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, value).commit() }

    fun getProfiles(): List<Profile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Profile(
                    userId = obj.getString("userId"),
                    displayName = obj.getString("displayName"),
                    initials = obj.getString("initials"),
                    lastUsed = obj.optLong("lastUsed", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveProfile(profile: Profile) {
        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.userId == profile.userId }
        if (index >= 0) {
            profiles[index] = profile  // update in-place, preserving insertion order
        } else {
            profiles.add(profile)
        }
        persistProfiles(profiles)
    }

    fun removeProfile(userId: String) {
        val profiles = getProfiles().filter { it.userId != userId }
        persistProfiles(profiles)
        if (activeProfileId == userId) {
            activeProfileId = profiles.maxByOrNull { it.lastUsed }?.userId
        }
    }

    fun hasProfiles(): Boolean = getProfiles().isNotEmpty()

    private fun persistProfiles(profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().apply {
                put("userId", p.userId)
                put("displayName", p.displayName)
                put("initials", p.initials)
                put("lastUsed", p.lastUsed)
            })
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).commit()
    }
}
