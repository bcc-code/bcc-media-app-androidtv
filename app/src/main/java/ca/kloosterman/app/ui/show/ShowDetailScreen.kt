package tv.brunstad.app.ui.show

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import tv.brunstad.app.R
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.brunstad.app.graphql.GetShowQuery
import tv.brunstad.app.ui.home.CardStyle
import tv.brunstad.app.ui.home.ContentCard
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ShowDetailScreen(
    onEpisodeClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ShowDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.reload()
        }
    }

    BackHandler { onBack() }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is ShowDetailViewModel.UiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.loading), fontSize = 24.sp)
                }
            is ShowDetailViewModel.UiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_prefix, s.message), fontSize = 18.sp)
                }
            is ShowDetailViewModel.UiState.Ready -> {
                    val isBookmarked by viewModel.isBookmarked.collectAsState()
                    ShowDetail(
                        show = s.show,
                        onEpisodeClick = onEpisodeClick,
                        isBookmarked = isBookmarked,
                        onToggleBookmark = { viewModel.toggleBookmark() }
                    )
                }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShowDetail(
    show: GetShowQuery.Show,
    onEpisodeClick: (String) -> Unit,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
) {
    val headerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { headerFocusRequester.requestFocus() }
    val focusManager = LocalFocusManager.current

    TvLazyColumn(
        contentPadding = PaddingValues(vertical = 32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 16.dp)
                    .focusRequester(headerFocusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            focusManager.moveFocus(FocusDirection.Next)
                            true
                        } else false
                    }
                    .focusable(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                AsyncImage(
                    model = show.image,
                    contentDescription = show.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(240.dp)
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = show.title, style = MaterialTheme.typography.headlineMedium)
                    show.description?.let { desc ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isBookmarked) stringResource(R.string.in_bookmarks) else stringResource(R.string.add_to_bookmarks))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        items(show.seasons?.items ?: emptyList()) { season ->
            SeasonRow(season = season, onEpisodeClick = onEpisodeClick)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonRow(
    season: GetShowQuery.Item,
    onEpisodeClick: (String) -> Unit
) {
    val episodes = season.episodes?.items ?: emptyList()
    if (episodes.isEmpty()) return

    val seasonLabel = season.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.season_label, season.number ?: 0)

    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            text = seasonLabel,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
        )
        TvLazyRow(contentPadding = PaddingValues(horizontal = 48.dp)) {
            items(episodes) { episode ->
                val dur = episode.duration?.toInt() ?: 0
                val prog = episode.progress?.toInt() ?: 0
                val effectivelyWatched = (episode.watched ?: false) &&
                    (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                    else (prog.toFloat() / dur).coerceIn(0f, 1f)
                val timeRemaining = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                    else stringResource(R.string.time_remaining, ((dur - prog) / 60).coerceAtLeast(1))
                ContentCard(
                    title = episode.title,
                    imageUrl = episode.image,
                    watched = effectivelyWatched,
                    progressFraction = progressFraction,
                    subtitle = timeRemaining,
                    style = CardStyle.LANDSCAPE,
                    onClick = { onEpisodeClick(episode.id) },
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}
