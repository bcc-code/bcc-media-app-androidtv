package tv.brunstad.app.data

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.npaw.NpawPluginProvider
import com.npaw.analytics.video.VideoAdapter
import com.npaw.media3.exoplayer.Media3ExoPlayerAdapter
import tv.brunstad.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NpawManager @Inject constructor() {

    private var videoAdapter: VideoAdapter? = null
    private var sessionId: String? = null
    private var anonymousId: String? = null
    private var ageGroup: String? = null

    private val isAvailable: Boolean
        get() = NpawPluginProvider.getInstance() != null

    fun updateUserOptions(anonymousId: String?, sessionId: String, ageGroup: String? = null) {
        this.sessionId = sessionId
        this.anonymousId = anonymousId
        this.ageGroup = ageGroup
        if (!isAvailable) return
        val opts = NpawPluginProvider.getInstance()?.analyticsOptions ?: return
        opts.username = anonymousId
    }

    fun startVideoAdapter(context: Context, player: ExoPlayer) {
        stopVideoAdapter()
        if (!isAvailable) return
        val plugin = NpawPluginProvider.getInstance() ?: return
        try {
            videoAdapter = plugin.videoBuilder()
                .setPlayerAdapter(Media3ExoPlayerAdapter(context, player))
                .build()
        } catch (e: Exception) {
            android.util.Log.e("NpawManager", "Failed to start video adapter", e)
            videoAdapter = null
        }
    }

    fun stopVideoAdapter() {
        try {
            videoAdapter?.destroy()
        } catch (e: Exception) {
            android.util.Log.e("NpawManager", "Failed to stop video adapter", e)
        }
        videoAdapter = null
    }

    fun updateContentMetadata(
        contentId: String,
        episodeTitle: String?,
        showTitle: String?,
        seasonTitle: String?,
        audioLanguage: String?,
        subtitleLanguage: String?,
        isLive: Boolean = false
    ) {
        if (!isAvailable) return
        val plugin = NpawPluginProvider.getInstance() ?: return
        val opts = plugin.analyticsOptions

        // Content metadata
        opts.contentId = contentId
        opts.contentTitle = episodeTitle
        opts.contentEpisodeTitle = episodeTitle
        opts.contentTvShow = showTitle
        opts.contentSeason = seasonTitle
        opts.contentLanguage = audioLanguage
        opts.contentSubtitles = subtitleLanguage
        opts.live = isLive

        // User/session metadata
        opts.username = anonymousId
        opts.contentCustomDimension1 = sessionId
        opts.contentCustomDimension2 = ageGroup
        opts.appReleaseVersion = BuildConfig.VERSION_NAME
    }
}
