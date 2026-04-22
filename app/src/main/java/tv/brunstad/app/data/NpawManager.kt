package tv.brunstad.app.data

import android.app.Activity
import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.npaw.NpawPlugin
import com.npaw.NpawPluginProvider
import com.npaw.analytics.video.VideoAdapter
import com.npaw.core.options.AnalyticsOptions
import com.npaw.media3.exoplayer.Media3ExoPlayerAdapter
import tv.brunstad.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NpawManager @Inject constructor() {

    private var plugin: NpawPlugin? = null

    fun initialize(activity: Activity) {
        if (plugin != null) return
        val options = AnalyticsOptions().apply {
            isAutoDetectBackground = false
            isParseManifest = true
            isEnabled = true
            appName = "bccm-androidtv"
            userObfuscateIp = true
            deviceIsAnonymous = false
        }
        NpawPluginProvider.initialize(
            ACCOUNT_CODE,
            activity,
            options
        )
        plugin = NpawPluginProvider.getInstance()
    }

    fun createVideoAdapter(context: Context, player: ExoPlayer): VideoAdapter? {
        val p = plugin ?: return null
        return p.videoBuilder()
            .setPlayerAdapter(Media3ExoPlayerAdapter(context, player))
            .build()
    }

    fun updateContentMetadata(
        contentId: String,
        episodeTitle: String?,
        showTitle: String?,
        seasonTitle: String?,
        audioLanguage: String?,
        subtitleLanguage: String?
    ) {
        val opts = plugin?.analyticsOptions ?: return
        opts.contentId = contentId
        opts.contentEpisodeTitle = episodeTitle
        opts.contentTvShow = showTitle
        opts.contentSeason = seasonTitle
        opts.contentLanguage = audioLanguage
        opts.contentSubtitles = subtitleLanguage
        opts.live = false
    }

    companion object {
        private val ACCOUNT_CODE = BuildConfig.NPAW_ACCOUNT_CODE
    }
}
