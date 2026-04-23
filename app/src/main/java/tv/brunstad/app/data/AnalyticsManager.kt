package tv.brunstad.app.data

import android.app.Application
import com.rudderstack.sdk.kotlin.android.Analytics
import com.rudderstack.sdk.kotlin.android.Configuration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tv.brunstad.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsManager @Inject constructor(
    private val languageRepository: LanguageRepository
) {

    private fun track(eventName: String, properties: JsonObject) {
        val client = instance ?: return
        try {
            val merged = buildJsonObject {
                commonProperties().forEach { (k, v) -> put(k, v) }
                properties.forEach { (k, v) -> put(k, v) }
            }
            client.track(name = eventName, properties = merged)
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsManager", "Failed to track event: $eventName", e)
        }
    }

    fun screen(screenName: String) {
        val client = instance ?: return
        try {
            client.screen(screenName = screenName, properties = buildJsonObject {
                commonProperties().forEach { (k, v) -> put(k, v) }
            })
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsManager", "Failed to track screen: $screenName", e)
        }
    }

    fun identify(userId: String, traits: JsonObject) {
        val client = instance ?: return
        try {
            client.identify(userId = userId, traits = traits)
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsManager", "Failed to identify user", e)
        }
    }

    // --- Event methods ---

    fun trackApplicationOpened(reason: String, coldStart: Boolean) {
        track("application_opened", buildJsonObject {
            put("reason", reason)
            put("coldStart", coldStart)
        })
    }

    fun trackSectionClicked(
        sectionId: String,
        sectionName: String,
        sectionPosition: Int,
        sectionType: String,
        elementPosition: Int,
        elementType: String,
        elementId: String,
        elementName: String,
        pageCode: String
    ) {
        track("section_clicked", buildJsonObject {
            put("sectionId", sectionId)
            put("sectionName", sectionName)
            put("sectionPosition", sectionPosition)
            put("sectionType", sectionType)
            put("elementPosition", elementPosition)
            put("elementType", elementType)
            put("elementId", elementId)
            put("elementName", elementName)
            put("pageCode", pageCode)
        })
    }

    fun trackSearchPerformed(searchText: String, searchLatency: Double, searchResultCount: Int) {
        track("search_performed", buildJsonObject {
            put("searchText", searchText)
            put("searchLatency", searchLatency)
            put("searchResultCount", searchResultCount)
        })
    }

    fun trackSearchResultClicked(
        searchText: String,
        elementPosition: Int,
        elementType: String,
        elementId: String,
        group: String
    ) {
        track("searchresult_clicked", buildJsonObject {
            put("searchText", searchText)
            put("elementPosition", elementPosition)
            put("elementType", elementType)
            put("elementId", elementId)
            put("group", group)
        })
    }

    fun trackLanguageChanged(pageCode: String, languageFrom: String, languageTo: String) {
        track("language_changed", buildJsonObject {
            put("pageCode", pageCode)
            put("languageFrom", languageFrom)
            put("languageTo", languageTo)
        })
    }

    fun trackPlaybackStarted(
        sessionId: String,
        contentPodId: String,
        position: Long,
        totalLength: Long
    ) {
        track("playback_started", buildJsonObject {
            put("sessionId", sessionId)
            put("contentPodId", contentPodId)
            put("position", position)
            put("totalLength", totalLength)
            put("videoPlayer", "Media3ExoPlayer")
            put("fullScreen", true)
            put("hasVideo", true)
        })
    }

    fun trackPlaybackPaused(
        sessionId: String,
        contentPodId: String,
        position: Long,
        totalLength: Long
    ) {
        track("playback_paused", buildJsonObject {
            put("sessionId", sessionId)
            put("contentPodId", contentPodId)
            put("position", position)
            put("totalLength", totalLength)
            put("videoPlayer", "Media3ExoPlayer")
            put("fullScreen", true)
            put("hasVideo", true)
        })
    }

    fun trackDeepLinkOpened(url: String) {
        track("deep_link_opened", buildJsonObject {
            put("url", url)
        })
    }

    fun trackChapterClicked(episodeId: String, chapterId: String, chapterTitle: String, chapterPosition: Int) {
        track("chapter_clicked", buildJsonObject {
            put("episodeId", episodeId)
            put("chapterId", chapterId)
            put("chapterTitle", chapterTitle)
            put("chapterPosition", chapterPosition)
        })
    }

    fun trackVideoPlayed(videoId: String, referenceId: String) {
        track("video_played", buildJsonObject {
            put("videoId", videoId)
            put("referenceId", referenceId)
        })
    }

    // --- Helpers ---

    private fun commonProperties(): Map<String, String> = mapOf(
        "channel" to "tv",
        "appName" to "bccm-androidtv",
        "appLanguage" to languageRepository.getLanguage(),
        "releaseVersion" to BuildConfig.VERSION_NAME,
        "sessionId" to tv.brunstad.app.di.AppModule.sessionId
    )

    companion object {
        private var instance: Analytics? = null

        fun initialize(writeKey: String, dataPlaneUrl: String, application: Application) {
            if (writeKey.isEmpty() || dataPlaneUrl.isEmpty()) return
            try {
                instance = Analytics(
                    configuration = Configuration(
                        writeKey = writeKey,
                        application = application,
                        dataPlaneUrl = dataPlaneUrl
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("AnalyticsManager", "Failed to initialize Rudderstack", e)
            }
        }
    }
}
