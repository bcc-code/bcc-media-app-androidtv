package tv.brunstad.app.ui.person

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import tv.brunstad.app.R
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.brunstad.app.graphql.GetPersonQuery
import tv.brunstad.app.ui.home.CardStyle
import tv.brunstad.app.ui.home.ContentCard
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onEpisodeClick: (episodeId: String, startPosition: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    BackHandler { onBack() }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is PersonDetailViewModel.UiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.loading), fontSize = 24.sp)
                }
            is PersonDetailViewModel.UiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_prefix, s.message), fontSize = 18.sp)
                }
            is PersonDetailViewModel.UiState.Ready ->
                PersonDetail(
                    person = s.person,
                    contributions = s.contributions,
                    allContributions = s.allContributions,
                    filters = s.filters,
                    selectedFilterCode = s.selectedFilterCode,
                    isFilterLoading = s.isFilterLoading,
                    onFilterSelected = { code -> viewModel.selectFilter(code) },
                    onEpisodeClick = onEpisodeClick
                )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonDetail(
    person: GetPersonQuery.Person,
    contributions: List<ContributionItem>,
    allContributions: List<ContributionItem>,
    filters: List<ContentFilter>,
    selectedFilterCode: String?,
    isFilterLoading: Boolean,
    onFilterSelected: (String?) -> Unit,
    onEpisodeClick: (episodeId: String, startPosition: Int) -> Unit
) {
    val firstCardFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    TvLazyColumn(
        contentPadding = PaddingValues(vertical = 32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Header: avatar + name + contribution count
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = person.image,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    val total = filters.sumOf { it.count }.takeIf { it > 0 } ?: contributions.size
                    if (total > 0) {
                        Text(
                            text = if (total == 1) stringResource(R.string.contributions_count_label, total) else stringResource(R.string.contributions_count_label_plural, total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (allContributions.isNotEmpty()) {
                        Button(onClick = {
                            val item = allContributions.random()
                            onEpisodeClick(item.episodeId, item.startPosition)
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.play_random))
                        }
                    }
                }
            }
        }

        // Filter buttons (only shown when there are multiple content types)
        if (filters.size > 1) {
            item {
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    item {
                        val total = filters.sumOf { it.count }.takeIf { it > 0 } ?: contributions.size
                        FilterButton(
                            label = stringResource(R.string.filter_all, total),
                            selected = selectedFilterCode == null,
                            onClick = { onFilterSelected(null) }
                        )
                    }
                    items(filters) { filter ->
                        FilterButton(
                            label = "${filter.title} (${filter.count})",
                            selected = selectedFilterCode?.lowercase() == filter.code.lowercase(),
                            onClick = { onFilterSelected(filter.code) }
                        )
                    }
                }
            }
        }

        // Contributions grid (wrapping)
        if (isFilterLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge) }
            }
        } else if (contributions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.contributions_count, contributions.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .focusRequester(firstCardFocusRequester),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    contributions.forEach { item ->
                        ContentCard(
                            title = item.title,
                            imageUrl = item.imageUrl,
                            badge = item.badge,
                            style = CardStyle.LANDSCAPE,
                            onClick = { onEpisodeClick(item.episodeId, item.startPosition) }
                        )
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_contributions),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(shape = shape),
        colors = ButtonDefaults.colors(
            // Not focused: filled if selected, subtle if not
            containerColor = if (selected) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurface,
            // Focused: keep same fill, border does the work
            focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                                  else MaterialTheme.colorScheme.onSurface
        ),
        border = ButtonDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(
                    width = 2.dp,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary
                ),
                shape = shape
            )
        ),
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1.05f),
        glow = ButtonDefaults.glow()
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
