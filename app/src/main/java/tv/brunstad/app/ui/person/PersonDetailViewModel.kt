package tv.brunstad.app.ui.person

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.brunstad.app.graphql.GetPersonContributionsQuery
import tv.brunstad.app.graphql.GetPersonQuery
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContributionItem(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val duration: Int,
    val showTitle: String?,
    val episodeId: String,
    val startPosition: Int = 0
)

data class ContentFilter(
    val code: String,
    val title: String,
    val count: Int
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val apollo: ApolloClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"])

    sealed class UiState {
        object Loading : UiState()
        data class Ready(
            val person: GetPersonQuery.Person,
            val contributions: List<ContributionItem>,   // current filtered view
            val allContributions: List<ContributionItem>, // full unfiltered list for Play Random
            val filters: List<ContentFilter>,
            val selectedFilterCode: String?,
            val isFilterLoading: Boolean = false
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    init { load() }

    fun selectFilter(code: String?) {
        val current = _state.value as? UiState.Ready ?: return
        _state.value = current.copy(selectedFilterCode = code, isFilterLoading = true)
        viewModelScope.launch {
            val contributions = fetchContributions(if (code != null) listOf(code) else null)
            val current2 = _state.value as? UiState.Ready ?: return@launch
            _state.value = current2.copy(contributions = contributions, isFilterLoading = false)
        }
    }

    private suspend fun fetchContributions(contentTypes: List<String>?): List<ContributionItem> {
        return runCatching {
            apollo.query(
                GetPersonContributionsQuery(
                    id = personId,
                    contentTypes = Optional.presentIfNotNull(contentTypes)
                )
            ).execute().dataOrThrow()
        }.mapCatching { data ->
            data.person?.contributions?.items?.mapNotNull { c ->
                c.item.onEpisode?.let { ep ->
                    ContributionItem(
                        id = ep.id,
                        title = ep.title,
                        imageUrl = ep.image,
                        duration = ep.duration ?: 0,
                        showTitle = ep.season?.show?.title,
                        episodeId = ep.id,
                        startPosition = 0
                    )
                } ?: c.item.onChapter?.let { ch ->
                    val ep = ch.episode ?: return@mapNotNull null
                    ContributionItem(
                        id = ch.id,
                        title = ep.title,
                        imageUrl = ep.image,
                        duration = ch.duration ?: 0,
                        showTitle = ep.season?.show?.title,
                        episodeId = ep.id,
                        startPosition = ch.start
                    )
                }
            } ?: emptyList()
        }.getOrElse { e ->
            Log.e("Person", "fetchContributions failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun load() {
        viewModelScope.launch {
            runCatching {
                apollo.query(GetPersonQuery(id = personId)).execute().dataOrThrow()
            }.onSuccess { data ->
                val person = data.person
                if (person == null) {
                    _state.value = UiState.Error("Person not found")
                    return@onSuccess
                }
                val contributions = person.contributions.items.mapNotNull { c ->
                    c.item.onEpisode?.let { ep ->
                        ContributionItem(
                            id = ep.id,
                            title = ep.title,
                            imageUrl = ep.image,
                            duration = ep.duration ?: 0,
                            showTitle = ep.season?.show?.title,
                            episodeId = ep.id,
                            startPosition = 0
                        )
                    } ?: c.item.onChapter?.let { ch ->
                        val ep = ch.episode ?: return@mapNotNull null
                        ContributionItem(
                            id = ch.id,
                            title = ep.title,
                            imageUrl = ep.image,
                            duration = ch.duration ?: 0,
                            showTitle = ep.season?.show?.title,
                            episodeId = ep.id,
                            startPosition = ch.start
                        )
                    }
                }
                val filters = person.contributionContentTypes.map { ctc ->
                    ContentFilter(code = ctc.type.code, title = ctc.type.title, count = ctc.count)
                }
                _state.value = UiState.Ready(
                    person = person,
                    contributions = contributions,
                    allContributions = contributions,
                    filters = filters,
                    selectedFilterCode = null
                )
            }.onFailure { e ->
                Log.e("Person", "load failed: ${e.message}", e)
                _state.value = UiState.Error(e.message ?: "Failed to load")
            }
        }
    }
}
