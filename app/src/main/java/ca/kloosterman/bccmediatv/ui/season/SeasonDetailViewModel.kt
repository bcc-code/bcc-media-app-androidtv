package tv.brunstad.app.ui.season

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.brunstad.app.graphql.GetSeasonEpisodesQuery
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeasonDetailViewModel @Inject constructor(
    private val apollo: ApolloClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val seasonId: String = checkNotNull(savedStateHandle["seasonId"])

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val season: GetSeasonEpisodesQuery.Season) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    init { load(showLoading = true) }

    fun reload() { load(showLoading = false) }

    private fun load(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState.Loading
            runCatching {
                apollo.query(GetSeasonEpisodesQuery(seasonId = seasonId, firstEpisodes = Optional.present(100)))
                    .execute().dataOrThrow()
            }.onSuccess { data ->
                val season = data.season
                if (season != null) _state.value = UiState.Ready(season)
                else _state.value = UiState.Error("Season not found")
            }.onFailure { e ->
                if (_state.value !is UiState.Ready) _state.value = UiState.Error(e.message ?: "Failed to load season")
            }
        }
    }
}
