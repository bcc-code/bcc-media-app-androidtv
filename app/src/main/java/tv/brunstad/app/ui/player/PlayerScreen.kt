@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package tv.brunstad.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.ContextWrapper
import tv.brunstad.app.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import android.view.LayoutInflater
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LANGUAGE_DISPLAY_NAMES = mapOf(
    "en" to "English", "no" to "Norwegian", "de" to "German", "nl" to "Dutch",
    "fr" to "French", "es" to "Spanish", "pt" to "Portuguese", "it" to "Italian",
    "pl" to "Polish", "ru" to "Russian", "fi" to "Finnish", "hr" to "Croatian",
    "bg" to "Bulgarian", "ro" to "Romanian", "sl" to "Slovenian", "hu" to "Hungarian",
    "ta" to "Tamil", "tr" to "Turkish"
)

fun languageDisplayName(code: String): String =
    LANGUAGE_DISPLAY_NAMES[code.lowercase()] ?: code.uppercase()

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onEpisodeEnded: (nextEpisodeId: String?) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val trackSelector = remember { DefaultTrackSelector(context) }
    val player = remember {
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).build()
    }
    val npawManager = viewModel.npawManager

    // Bind NPAW video adapter to the player
    DisposableEffect(player) {
        npawManager.startVideoAdapter(context, player)
        onDispose { npawManager.stopVideoAdapter() }
    }

    // Update NPAW content metadata when episode info is available
    LaunchedEffect(uiState.episodeTitle, uiState.selectedAudioLanguage, uiState.selectedSubtitleLanguage) {
        if (uiState.streamUrl == null) return@LaunchedEffect
        npawManager.updateContentMetadata(
            contentId = viewModel.episodeId,
            episodeTitle = uiState.episodeTitle,
            showTitle = uiState.showTitle,
            seasonTitle = uiState.seasonTitle ?: uiState.seasonNumber?.toString(),
            audioLanguage = uiState.selectedAudioLanguage,
            subtitleLanguage = uiState.selectedSubtitleLanguage
        )
    }

    val startProgressSeconds = viewModel.startProgressSeconds
    var controlsVisible by remember { mutableStateOf(true) }
    var currentChapterTitle by remember { mutableStateOf<String?>(null) }

    // Poll current chapter every second while controls are visible
    LaunchedEffect(controlsVisible, uiState.chapters.isNotEmpty()) {
        if (!controlsVisible || uiState.chapters.isEmpty()) return@LaunchedEffect
        while (true) {
            val posSec = (player.currentPosition / 1000).toInt()
            val ch = uiState.chapters.lastOrNull { it.startSeconds <= posSec }
            currentChapterTitle = if (ch != null && (ch.durationSeconds == null || posSec < ch.startSeconds + ch.durationSeconds)) ch.title else null
            delay(1_000)
        }
    }

    BackHandler { onBack() }

    // When episode ends, hand off to the caller (navigate to next episode detail or back)
    LaunchedEffect(uiState.episodeEnded) {
        if (uiState.episodeEnded) {
            onEpisodeEnded(uiState.nextEpisodeId)
        }
    }

    LaunchedEffect(uiState.selectedAudioLanguage) {
        val lang = uiState.selectedAudioLanguage ?: return@LaunchedEffect
        trackSelector.setParameters(
            trackSelector.buildUponParameters().setPreferredAudioLanguage(lang)
        )
    }

    LaunchedEffect(uiState.selectedSubtitleLanguage) {
        val lang = uiState.selectedSubtitleLanguage
        if (lang != null) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setPreferredTextLanguage(lang)
                    .setIgnoredTextSelectionFlags(0)
            )
        } else {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setPreferredTextLanguage(null)
                    .setIgnoredTextSelectionFlags(
                        androidx.media3.common.C.SELECTION_FLAG_DEFAULT or
                        androidx.media3.common.C.SELECTION_FLAG_FORCED
                    )
            )
        }
    }

    LaunchedEffect(uiState.streamUrl) {
        val url = uiState.streamUrl ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(url))
        // Set NPAW content metadata before prepare() so the "start" event includes it
        npawManager.updateContentMetadata(
            contentId = viewModel.episodeId,
            episodeTitle = uiState.episodeTitle,
            showTitle = uiState.showTitle,
            seasonTitle = uiState.seasonTitle ?: uiState.seasonNumber?.toString(),
            audioLanguage = uiState.selectedAudioLanguage,
            subtitleLanguage = uiState.selectedSubtitleLanguage
        )
        if (startProgressSeconds > 0) {
            var seekDone = false
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!seekDone && playbackState == Player.STATE_READY) {
                        seekDone = true
                        val durMs = player.duration
                        val seekMs = startProgressSeconds * 1000L
                        if (durMs <= 0 || seekMs < durMs) player.seekTo(seekMs)
                        player.removeListener(this)
                    }
                }
            }
            player.addListener(listener)
        }
        player.prepare()
        player.playWhenReady = true
        viewModel.trackPlaybackStarted(startProgressSeconds * 1000L, uiState.episodeDurationSeconds?.times(1000L) ?: 0L)
    }

    // Save progress every 10 seconds while playing
    val scope = rememberCoroutineScope()
    LaunchedEffect(uiState.streamUrl) {
        if (uiState.streamUrl == null) return@LaunchedEffect
        while (true) {
            delay(10_000)
            val pos = player.currentPosition
            val dur = player.duration
            if (pos > 0 && dur > 0) viewModel.saveProgress((pos / 1000).toInt(), (dur / 1000).toInt())
        }
    }

    // Save on pause and trigger auto-play on completion
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    if (pos > 0 && dur > 0) {
                        viewModel.saveProgress((pos / 1000).toInt(), (dur / 1000).toInt())
                        viewModel.trackPlaybackPaused(pos, dur)
                    }
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val dur = player.duration
                    if (dur > 0) viewModel.saveProgress((dur / 1000).toInt(), (dur / 1000).toInt())
                    viewModel.onPlaybackEnded()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = player.currentPosition
            val dur = player.duration
            if (pos > 0 && dur > 0) {
                scope.launch { viewModel.saveProgress((pos / 1000).toInt(), (dur / 1000).toInt()) }
            }
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                    this.player = player
                    useController = true
                    keepScreenOn = true
                    setShowSubtitleButton(true)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == android.view.View.VISIBLE
                        if (visibility == android.view.View.GONE) requestFocus()
                    })
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize()
        )

        // Episode info overlay — shown when player controls are visible, sits above the PlayerView
        if (controlsVisible && (uiState.showTitle != null || uiState.episodeTitle != null)) {
            androidx.tv.material3.Surface(
                modifier = Modifier.padding(start = 32.dp, top = 32.dp),
                shape = RoundedCornerShape(8.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                val seasonLabel = uiState.seasonTitle
                    ?: uiState.seasonNumber?.let { stringResource(R.string.season_label, it) }
                val headerLine = buildString {
                    uiState.showTitle?.let { append(it) }
                    seasonLabel?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append(it)
                    }
                }
                if (headerLine.isNotEmpty()) {
                    Text(headerLine, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }
                uiState.episodeTitle?.let {
                    Text(it, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                currentChapterTitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.6f))
                }
            }
            }
        }

    }
}
