package ca.kloosterman.bccmediatv.ui.episode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kloosterman.bccmediatv.data.LanguageRepository
import ca.kloosterman.bccmediatv.data.MyListRepository
import ca.kloosterman.bccmediatv.graphql.GetEpisodeDetailQuery
import com.apollographql.apollo.ApolloClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val apollo: ApolloClient,
    private val myListRepository: MyListRepository,
    private val languageRepository: LanguageRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])

    val autoPlayDelay: Int get() = languageRepository.getAutoPlayDelay()
    val autoPlayEnabled: Boolean get() = autoPlayDelay > 0

    private val _autoPlayCountdown = MutableStateFlow<Int?>(null)
    val autoPlayCountdown: StateFlow<Int?> = _autoPlayCountdown.asStateFlow()

    private var countdownJob: Job? = null
    private var hasStartedCountdown = false

    fun startAutoPlayCountdown() {
        if (!autoPlayEnabled || hasStartedCountdown) return
        hasStartedCountdown = true
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in autoPlayDelay downTo 1) {
                _autoPlayCountdown.value = i
                delay(1_000)
            }
            _autoPlayCountdown.value = 0  // signal: play now
        }
    }

    fun cancelAutoPlay() {
        countdownJob?.cancel()
        countdownJob = null
        _autoPlayCountdown.value = null
    }

    /** Call this before triggering play so countdown doesn't re-fire if the screen is revisited. */
    fun consumeAutoPlay() {
        countdownJob?.cancel()
        countdownJob = null
        _autoPlayCountdown.value = null
    }

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val episode: GetEpisodeDetailQuery.Episode) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    val isBookmarked: StateFlow<Boolean> = myListRepository.entryMap
        .map { it.containsKey(episodeId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        load(showLoading = true)
        viewModelScope.launch { myListRepository.load() }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            if (myListRepository.isInList(episodeId)) myListRepository.remove(episodeId)
            else myListRepository.addEpisode(episodeId)
        }
    }

    fun reload() { load(showLoading = false) }

    private fun load(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState.Loading
            runCatching {
                apollo.query(GetEpisodeDetailQuery(episodeId = episodeId)).execute().dataOrThrow()
            }.onSuccess { data ->
                val ep = data.episode
                if (ep != null) _state.value = UiState.Ready(ep)
                else _state.value = UiState.Error("Episode not found")
            }.onFailure { e ->
                if (_state.value !is UiState.Ready) _state.value = UiState.Error(e.message ?: "Failed to load episode")
            }
        }
    }
}
