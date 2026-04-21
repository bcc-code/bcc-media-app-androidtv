package tv.brunstad.app.ui.page

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.brunstad.app.graphql.GetPageQuery
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PageViewModel @Inject constructor(
    private val apollo: ApolloClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val pageCode: String = checkNotNull(savedStateHandle["code"])

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val title: String, val sections: List<GetPageQuery.Item>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            runCatching {
                apollo.query(
                    GetPageQuery(
                        code = pageCode,
                        first = Optional.present(50),
                        offset = Optional.present(0)
                    )
                ).execute().dataOrThrow()
            }.onSuccess { data ->
                val page = data.page
                if (page != null) {
                    _state.value = UiState.Ready(
                        title = page.title,
                        sections = page.sections.items
                    )
                } else {
                    _state.value = UiState.Error("Page not found")
                }
            }.onFailure { e ->
                _state.value = UiState.Error(e.message ?: "Failed to load page")
            }
        }
    }
}
