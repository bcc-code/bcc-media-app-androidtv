package tv.brunstad.app.data

import android.content.SharedPreferences
import tv.brunstad.app.auth.ProfileStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageRepository @Inject constructor(
    private val prefs: SharedPreferences,
    private val profileStore: ProfileStore
) {
    companion object {
        const val KEY = "content_language"
        const val AUDIO_KEY = "preferred_audio_language"
        const val SUBTITLE_KEY = "preferred_subtitle_language"
        const val AUTOPLAY_DELAY_KEY = "autoplay_delay_seconds"
        const val DEFAULT = "en"

        val SUPPORTED_LANGUAGES = listOf(
            "en" to "English",
            "no" to "Norwegian",
            "de" to "German",
            "nl" to "Dutch",
            "fr" to "French",
            "es" to "Spanish",
            "pt" to "Portuguese",
            "it" to "Italian",
            "pl" to "Polish",
            "ro" to "Romanian",
            "ru" to "Russian",
            "fi" to "Finnish",
            "hr" to "Croatian",
            "bg" to "Bulgarian",
            "sl" to "Slovenian",
            "hu" to "Hungarian",
            "ta" to "Tamil",
            "tr" to "Turkish"
        )

        /** Constructs the profile-aware language pref key; used by attachBaseContext before DI is ready. */
        fun profileLanguageKey(userId: String?) =
            if (userId != null) "profile_${userId}_$KEY" else KEY
    }

    private val _languageChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits whenever the language has changed — listeners should reload content. */
    val languageChanged: SharedFlow<Unit> = _languageChanged

    private fun key(base: String): String {
        val uid = profileStore.activeProfileId ?: return base
        val profileKey = "profile_${uid}_$base"
        // One-time migration: copy legacy un-prefixed value to profile-prefixed key on first access
        if (!prefs.contains(profileKey) && prefs.contains(base)) {
            val editor = prefs.edit()
            if (base == AUTOPLAY_DELAY_KEY) {
                editor.putInt(profileKey, prefs.getInt(base, 10))
            } else {
                prefs.getString(base, null)?.let { editor.putString(profileKey, it) }
            }
            editor.apply()
        }
        return profileKey
    }

    fun getLanguage(): String = prefs.getString(key(KEY), DEFAULT) ?: DEFAULT

    fun hasLanguageSet(): Boolean = prefs.contains(key(KEY))

    fun clearLanguage() {
        prefs.edit().remove(key(KEY)).apply()
    }

    fun setLanguage(code: String) {
        prefs.edit().putString(key(KEY), code).commit()
        _languageChanged.tryEmit(Unit)
    }

    fun getAudioLanguage(): String = prefs.getString(key(AUDIO_KEY), DEFAULT) ?: DEFAULT

    fun setAudioLanguage(code: String) {
        prefs.edit().putString(key(AUDIO_KEY), code).apply()
    }

    fun getSubtitleLanguage(): String? = prefs.getString(key(SUBTITLE_KEY), "")?.ifEmpty { null }

    fun setSubtitleLanguage(code: String?) {
        prefs.edit().putString(key(SUBTITLE_KEY), code ?: "").apply()
    }

    fun getAutoPlayDelay(): Int = prefs.getInt(key(AUTOPLAY_DELAY_KEY), 10)

    fun setAutoPlayDelay(seconds: Int) {
        prefs.edit().putInt(key(AUTOPLAY_DELAY_KEY), seconds).apply()
    }

    /** Signals listeners (e.g. HomeViewModel) to reload content — used when switching profiles. */
    fun emitLanguageChanged() {
        _languageChanged.tryEmit(Unit)
    }
}
