package ca.kloosterman.bccmediatv.ui.episode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import ca.kloosterman.bccmediatv.R
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.kloosterman.bccmediatv.graphql.GetEpisodeDetailQuery
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    onPlay: (progress: Int) -> Unit,
    onBack: () -> Unit,
    onShowClick: (String) -> Unit = {},
    onSeasonClick: (String) -> Unit = {},
    chapterOffset: Int = 0,
    fromAutoplay: Boolean = false,
    viewModel: EpisodeDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val autoPlayCountdown by viewModel.autoPlayCountdown.collectAsState()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.reload()
        }
    }

    BackHandler { viewModel.cancelAutoPlay(); onBack() }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is EpisodeDetailViewModel.UiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.loading), fontSize = 24.sp)
                }
            is EpisodeDetailViewModel.UiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_prefix, s.message), fontSize = 18.sp)
                }
            is EpisodeDetailViewModel.UiState.Ready -> {
                val isBookmarked by viewModel.isBookmarked.collectAsState()
                val playOffset = if (chapterOffset > 0) chapterOffset
                                 else s.episode.progress?.toInt() ?: 0

                // Start countdown once when arriving via auto-play
                LaunchedEffect(fromAutoplay) {
                    if (fromAutoplay) viewModel.startAutoPlayCountdown()
                }

                // Auto-trigger play when countdown reaches 0 — always restart (not resume)
                LaunchedEffect(autoPlayCountdown) {
                    if (autoPlayCountdown == 0) {
                        viewModel.consumeAutoPlay()
                        onPlay(0)
                    }
                }

                // Focus requester for the Cancel button — declared outside the conditional so
                // LaunchedEffect can fire after layout completes
                val cancelFocusRequester = remember { FocusRequester() }
                val countdownActive = autoPlayCountdown != null && autoPlayCountdown!! > 0
                LaunchedEffect(countdownActive) {
                    if (countdownActive) {
                        delay(50)
                        try { cancelFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    EpisodeDetail(
                        episode = s.episode,
                        chapterOffset = chapterOffset,
                        onResume = { viewModel.cancelAutoPlay(); onPlay(playOffset) },
                        onRestart = { viewModel.cancelAutoPlay(); onPlay(0) },
                        onPlayFromChapter = { start -> viewModel.cancelAutoPlay(); onPlay(start) },
                        onShowClick = onShowClick,
                        onSeasonClick = onSeasonClick,
                        isBookmarked = isBookmarked,
                        onToggleBookmark = { viewModel.toggleBookmark() }
                    )

                    // Auto-play countdown overlay at bottom
                    if (countdownActive) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 48.dp)
                                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.playing_in_seconds, autoPlayCountdown!!),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                                Button(
                                    onClick = { viewModel.cancelAutoPlay() },
                                    modifier = Modifier.focusRequester(cancelFocusRequester)
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeDetail(
    episode: GetEpisodeDetailQuery.Episode,
    chapterOffset: Int = 0,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onPlayFromChapter: (Int) -> Unit = {},
    onShowClick: (String) -> Unit = {},
    onSeasonClick: (String) -> Unit = {},
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
) {
    var showChapters by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        // Left: metadata + play button
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            // Show / season breadcrumb — each part is individually tappable
            val showTitle = episode.season?.show?.title
            val showId = episode.season?.show?.id
            val seasonId = episode.season?.id
            val seasonNum = episode.season?.number
            val seasonTitle = episode.season?.title?.takeIf { it.isNotBlank() }
                ?: episode.season?.number?.let { stringResource(R.string.season_label, it) }
            if (showTitle != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BreadcrumbLink(
                        text = showTitle,
                        onClick = if (showId != null) ({ onShowClick(showId) }) else null
                    )
                    if (seasonTitle != null) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        BreadcrumbLink(
                            text = seasonTitle,
                            onClick = if (seasonId != null) ({ onSeasonClick(seasonId) }) else null
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(text = episode.title, style = MaterialTheme.typography.headlineMedium)

            val hasMeta = episode.duration != null || episode.ageRating != null
            if (hasMeta) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    episode.duration?.let {
                        Text(
                            text = formatDuration(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    episode.ageRating?.let { rating ->
                        Text(
                            text = formatAgeRating(rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            episode.description?.let { desc ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.height(32.dp))

            val progress = episode.progress?.toInt() ?: 0
            val duration = episode.duration?.toInt() ?: 0
            val effectivelyWatched = episode.watched == true &&
                (duration <= 0 || progress <= 0 || progress.toFloat() / duration >= 0.95f)
            // chapterOffset takes priority over saved progress for the primary play action
            val hasProgress = chapterOffset > 0 ||
                (progress > 0 && duration > 0 && !effectivelyWatched)
            val displayOffset = if (chapterOffset > 0) chapterOffset else progress

            // Progress bar (only for saved API progress, not chapter offset)
            if (hasProgress && chapterOffset == 0) {
                val fraction = (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(Color(0xFFE53935), shape = RoundedCornerShape(2.dp))
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            val primaryFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { primaryFocusRequester.requestFocus() }

            if (hasProgress) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.focusRequester(primaryFocusRequester)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (chapterOffset > 0) stringResource(R.string.play_from, formatTime(chapterOffset))
                            else stringResource(R.string.continue_from, formatTime(progress)),
                            fontSize = 16.sp
                        )
                    }
                    Button(
                        onClick = onRestart,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start_over), fontSize = 16.sp)
                    }
                }
            } else {
                Button(
                    onClick = onResume,
                    modifier = Modifier.focusRequester(primaryFocusRequester)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play), fontSize = 18.sp)
                }
            }

            // Current chapter hint — shown when episode is in progress and within a chapter
            val currentChapter = if (hasProgress && chapterOffset == 0) {
                currentChapterAt(episode.chapters, displayOffset)
            } else null
            if (currentChapter != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = currentChapter,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isBookmarked) stringResource(R.string.in_bookmarks) else stringResource(R.string.add_to_bookmarks), fontSize = 16.sp)
                }
                if (!episode.chapters.isNullOrEmpty()) {
                    Button(onClick = { showChapters = true }) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${episode.chapters.size}", fontSize = 16.sp)
                    }
                }
            }
        }

        // Right: thumbnail
        Box(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = episode.image,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.large)
            )
        }
    } // Row

    if (showChapters) {
        ChapterDialog(
            chapters = episode.chapters ?: emptyList(),
            progressSeconds = if (chapterOffset > 0) chapterOffset else (episode.progress?.toInt() ?: 0),
            onDismiss = { showChapters = false },
            onChapterSelected = { start ->
                showChapters = false
                onPlayFromChapter(start)
            }
        )
    }
    } // outer Box
} // EpisodeDetail

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BreadcrumbLink(text: String, onClick: (() -> Unit)?) {
    if (onClick == null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        return
    }
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
        glow = ButtonDefaults.glow()
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChapterDialog(
    chapters: List<GetEpisodeDetailQuery.Chapter>,
    progressSeconds: Int = 0,
    onDismiss: () -> Unit,
    onChapterSelected: (Int) -> Unit
) {
    // Closest chapter at or before current progress (even if we're in a gap between chapters)
    val currentIndex = if (progressSeconds > 0)
        chapters.indexOfLast { it.start <= progressSeconds }.takeIf { it >= 0 }
    else null

    val focusIndex = currentIndex ?: 0
    val focusRequester = remember { FocusRequester() }
    val listState = rememberTvLazyListState()

    LaunchedEffect(Unit) {
        if (focusIndex > 0) listState.scrollToItem(focusIndex)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .fillMaxWidth(0.5f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(vertical = 24.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.chapters_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 32.dp, bottom = 16.dp)
                )
                TvLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(chapters) { index, chapter ->
                        val isCurrent = index == currentIndex
                        val label = buildString {
                            append(formatTime(chapter.start))
                            chapter.duration?.let { append(" · ${formatDuration(it)}") }
                        }
                        Button(
                            onClick = { onChapterSelected(chapter.start) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { if (index == focusIndex) it.focusRequester(focusRequester) else it },
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(0.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                focusedContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                            glow = ButtonDefaults.glow()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ${chapter.title ?: stringResource(R.string.chapter_fallback, index + 1)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun currentChapterAt(chapters: List<GetEpisodeDetailQuery.Chapter>?, progressSeconds: Int): String? {
    if (chapters.isNullOrEmpty() || progressSeconds <= 0) return null
    val ch = chapters.lastOrNull { it.start <= progressSeconds } ?: return null
    // If duration is known, only show the chapter while we're within it
    if (ch.duration != null && progressSeconds >= ch.start + ch.duration) return null
    return ch.title
}

private fun formatAgeRating(raw: String): String = when (raw.uppercase()) {
    "A"  -> "0+"
    else -> "${raw.uppercase()}+"
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m} min"
}

private fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
