package ca.kloosterman.bccmediatv.ui.show

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kloosterman.bccmediatv.data.MyListRepository
import ca.kloosterman.bccmediatv.graphql.GetShowQuery
import com.apollographql.apollo.ApolloClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShowDetailViewModel @Inject constructor(
    private val apollo: ApolloClient,
    private val myListRepository: MyListRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val showId: String = checkNotNull(savedStateHandle["showId"])

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val show: GetShowQuery.Show) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    val isBookmarked: StateFlow<Boolean> = myListRepository.entryMap
        .map { it.containsKey(showId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        load(showLoading = true)
        viewModelScope.launch { myListRepository.load() }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            if (myListRepository.isInList(showId)) {
                myListRepository.remove(showId)
            } else {
                val show = (_state.value as? UiState.Ready)?.show
                myListRepository.addShow(showId, show?.title ?: "", show?.image)
            }
        }
    }

    fun reload() { load(showLoading = false) }

    private fun load(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState.Loading
            runCatching {
                apollo.query(GetShowQuery(showId = showId)).execute().dataOrThrow()
            }.onSuccess { data ->
                val show = data.show
                if (show != null) _state.value = UiState.Ready(show)
                else _state.value = UiState.Error("Show not found")
            }.onFailure { e ->
                if (_state.value !is UiState.Ready) _state.value = UiState.Error(e.message ?: "Failed to load show")
            }
        }
    }
}
