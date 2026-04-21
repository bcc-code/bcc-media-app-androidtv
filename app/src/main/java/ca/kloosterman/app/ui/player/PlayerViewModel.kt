package tv.brunstad.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.brunstad.app.data.LanguageRepository
import tv.brunstad.app.data.WatchNextHelper
import tv.brunstad.app.graphql.GetEpisodeForAutoplayQuery
import tv.brunstad.app.graphql.GetEpisodeStreamsQuery
import tv.brunstad.app.graphql.SetEpisodeProgressMutation
import com.apollographql.apollo.ApolloClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Languages ordered by BCC congregation prevalence, not alphabetically
private val PREFERRED_LANGUAGE_ORDER = listOf(
    "no", "en", "de", "nl", "fr", "hr", "hu", "sl", "ro", "bg", "pl", "ru", "fi", "es", "pt", "it", "ta", "tr"
)

data class ChapterItem(val title: String, val startSeconds: Int, val durationSeconds: Int?)

data class PlayerUiState(
    val streamUrl: String? = null,
    val audioLanguages: List<String> = emptyList(),
    val subtitleLanguages: List<String> = emptyList(),
    val selectedAudioLanguage: String? = null,
    val selectedSubtitleLanguage: String? = null,  // null means subtitles off
    val nextEpisodeId: String? = null,
    val nextEpisodeTitle: String? = null,
    val episodeEnded: Boolean = false,
    val episodeTitle: String? = null,
    val episodeDescription: String? = null,
    val episodeImageUrl: String? = null,
    val episodeDurationSeconds: Int? = null,
    val showTitle: String? = null,
    val seasonTitle: String? = null,     // from API; null if blank
    val seasonNumber: Int? = null,        // fallback when seasonTitle is null
    val chapters: List<ChapterItem> = emptyList()
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val apollo: ApolloClient,
    private val languageRepository: LanguageRepository,
    private val watchNextHelper: WatchNextHelper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])
    val startProgressSeconds: Int = savedStateHandle["progress"] ?: 0

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                apollo.query(GetEpisodeStreamsQuery(id = episodeId)).execute().dataOrThrow()
            }.onSuccess { data ->
                val stream = data.episode?.streams
                    ?.firstOrNull { it.type.contains("hls", ignoreCase = true) }
                    ?: data.episode?.streams?.firstOrNull()

                val audioLangs = (stream?.audioLanguages?.filterNotNull() ?: emptyList())
                    .sortedBy { code -> PREFERRED_LANGUAGE_ORDER.indexOf(code).let { if (it < 0) Int.MAX_VALUE else it } }
                val subtitleLangs = stream?.subtitleLanguages?.filterNotNull() ?: emptyList()

                // Default audio language: use saved preference, fall back to first available
                val prefAudio = languageRepository.getAudioLanguage()
                val defaultAudio = when {
                    audioLangs.contains(prefAudio) -> prefAudio
                    else -> audioLangs.firstOrNull()
                }

                // Default subtitle language: use saved preference (null = off)
                val prefSubtitle = languageRepository.getSubtitleLanguage()
                val defaultSubtitle = if (prefSubtitle != null && subtitleLangs.contains(prefSubtitle)) prefSubtitle else null

                val ep = data.episode
                _uiState.value = PlayerUiState(
                    streamUrl = stream?.url,
                    audioLanguages = audioLangs,
                    subtitleLanguages = subtitleLangs,
                    selectedAudioLanguage = defaultAudio,
                    selectedSubtitleLanguage = defaultSubtitle,
                    episodeTitle = ep?.title,
                    episodeDescription = ep?.description,
                    episodeImageUrl = ep?.image,
                    episodeDurationSeconds = ep?.duration,
                    showTitle = ep?.season?.show?.title,
                    seasonTitle = ep?.season?.title?.takeIf { it.isNotBlank() },
                    seasonNumber = ep?.season?.number,
                    chapters = ep?.chapters
                        ?.mapNotNull { ch -> ch.title?.let { ChapterItem(it, ch.start, ch.duration) } }
                        ?: emptyList()
                )
                // Register in Google TV Continue Watching on playback start
                watchNextHelper.upsert(
                    episodeId = episodeId,
                    title = ep?.title ?: "",
                    description = ep?.description,
                    imageUrl = ep?.image,
                    durationMs = ep?.duration?.let { it * 1000L },
                    positionMs = startProgressSeconds * 1000L,
                    showTitle = ep?.season?.show?.title
                )
            }
        }
        // Fetch next episode info in the background
        viewModelScope.launch { fetchNextEpisode() }
    }

    private suspend fun fetchNextEpisode() {
        runCatching {
            apollo.query(GetEpisodeForAutoplayQuery(episodeId = episodeId)).execute().dataOrThrow()
        }.onSuccess { data ->
            val episode = data.episode ?: return
            val currentNumber = episode.number ?: return
            val seasonEpisodes = episode.season?.episodes?.items ?: return
            // Episodes may be in any order; find the one with number = currentNumber + 1
            val sorted = seasonEpisodes.sortedBy { it.number ?: Int.MAX_VALUE }
            val currentIdx = sorted.indexOfFirst { it.number == currentNumber }
            val nextEp = if (currentIdx >= 0 && currentIdx + 1 < sorted.size) sorted[currentIdx + 1] else null
            if (nextEp != null) {
                _uiState.value = _uiState.value.copy(
                    nextEpisodeId = nextEp.id,
                    nextEpisodeTitle = nextEp.title
                )
            }
        }
    }

    fun onPlaybackEnded() {
        _uiState.value = _uiState.value.copy(episodeEnded = true)
    }

    fun selectAudioLanguage(language: String) {
        _uiState.value = _uiState.value.copy(selectedAudioLanguage = language)
    }

    fun selectSubtitleLanguage(language: String?) {
        _uiState.value = _uiState.value.copy(selectedSubtitleLanguage = language)
    }

    fun saveProgress(positionSeconds: Int, durationSeconds: Int) {
        if (positionSeconds <= 0 || durationSeconds <= 0) return
        val isWatched = durationSeconds > 0 && positionSeconds >= durationSeconds * 19 / 20
        viewModelScope.launch {
            runCatching {
                apollo.mutation(
                    SetEpisodeProgressMutation(
                        id = episodeId,
                        progress = positionSeconds,
                        duration = durationSeconds
                    )
                ).execute()
            }
            val state = _uiState.value
            if (isWatched) {
                watchNextHelper.remove(episodeId)
            } else {
                watchNextHelper.upsert(
                    episodeId = episodeId,
                    title = state.episodeTitle ?: "",
                    description = state.episodeDescription,
                    imageUrl = state.episodeImageUrl,
                    durationMs = durationSeconds * 1000L,
                    positionMs = positionSeconds * 1000L,
                    showTitle = state.showTitle
                )
            }
        }
    }
}
