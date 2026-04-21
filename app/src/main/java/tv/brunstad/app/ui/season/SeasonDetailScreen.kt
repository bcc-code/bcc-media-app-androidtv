package tv.brunstad.app.ui.season

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.brunstad.app.graphql.GetSeasonEpisodesQuery
import tv.brunstad.app.ui.home.CardStyle
import tv.brunstad.app.ui.home.ContentCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonDetailScreen(
    onEpisodeClick: (String) -> Unit,
    onShowClick: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: SeasonDetailViewModel = hiltViewModel()
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
            is SeasonDetailViewModel.UiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.loading), fontSize = 24.sp)
                }
            is SeasonDetailViewModel.UiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_prefix, s.message), fontSize = 18.sp)
                }
            is SeasonDetailViewModel.UiState.Ready ->
                SeasonDetail(season = s.season, onEpisodeClick = onEpisodeClick, onShowClick = onShowClick)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonDetail(
    season: GetSeasonEpisodesQuery.Season,
    onEpisodeClick: (String) -> Unit,
    onShowClick: (String) -> Unit = {}
) {
    TvLazyColumn(
        contentPadding = PaddingValues(vertical = 32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                season.show?.let { show ->
                    val showId = show.id
                    Button(
                        onClick = { onShowClick(showId) },
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
                        Text(
                            text = show.title,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp)
                        )
                    }
                }
                val seasonLabel = season.title.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.season_label, season.number ?: 0)
                Text(text = seasonLabel, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            TvLazyRow(contentPadding = PaddingValues(horizontal = 48.dp)) {
                items(season.episodes?.items ?: emptyList()) { episode ->
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
}
