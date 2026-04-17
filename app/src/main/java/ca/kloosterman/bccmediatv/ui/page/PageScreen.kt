package ca.kloosterman.bccmediatv.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ca.kloosterman.bccmediatv.R
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.kloosterman.bccmediatv.ui.home.PageContent

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PageScreen(
    onEpisodeClick: (String) -> Unit,
    onPageClick: (String) -> Unit,
    onSeasonClick: (String) -> Unit = {},
    onShowClick: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: PageViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    BackHandler { onBack() }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is PageViewModel.UiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.loading), fontSize = 24.sp)
                }
            is PageViewModel.UiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_prefix, s.message), fontSize = 18.sp)
                }
            is PageViewModel.UiState.Ready ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(start = 48.dp, top = 32.dp, bottom = 8.dp)
                    )
                    // weight(1f) gives PageContent only the remaining height after the title,
                    // preventing the inner TvLazyColumn from overflowing the screen.
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        PageContent(
                            sections = s.sections,
                            preferGrid = true,
                            onEpisodeClick = onEpisodeClick,
                            onPageClick = onPageClick,
                            onSeasonClick = onSeasonClick,
                            onShowClick = onShowClick
                        )
                    }
                }
        }
    }
}
