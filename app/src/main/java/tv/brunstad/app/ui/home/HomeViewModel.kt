package tv.brunstad.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.brunstad.app.data.LanguageRepository
import tv.brunstad.app.data.PreviewChannelHelper
import tv.brunstad.app.data.PreviewProgramData
import tv.brunstad.app.graphql.GetApplicationQuery
import tv.brunstad.app.graphql.GetPageQuery
import tv.brunstad.app.graphql.SearchQuery
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tv.brunstad.app.R
import javax.inject.Inject

data class NavItem(
    val code: String,
    val title: String,
    val icon: NavIcon
)

const val MY_LIST_CODE = "__mylist__"

sealed class NavIcon {
    object HOME : NavIcon()
    object SEARCH : NavIcon()
    object SETTINGS : NavIcon()
    object BOOKMARK : NavIcon()
}

private fun GetPageQuery.Item.isMyListSection(): Boolean =
    onItemSection?.metadata?.myList == true ||
    onFeaturedSection?.metadata?.myList == true ||
    onDefaultSection?.metadata?.myList == true ||
    onPosterSection?.metadata?.myList == true ||
    onCardSection?.metadata?.myList == true ||
    onCardListSection?.metadata?.myList == true ||
    onListSection?.metadata?.myList == true ||
    onDefaultGridSection?.metadata?.myList == true ||
    onPosterGridSection?.metadata?.myList == true ||
    onIconGridSection?.metadata?.myList == true ||
    onAvatarSection?.metadata?.myList == true

data class PageState(
    val sections: List<GetPageQuery.Item> = emptyList(),
    val pageTitle: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

data class HomeUiState(
    val navItems: List<NavItem> = emptyList(),
    val myListTitle: String = "Watchlist",
    val selectedCode: String = "",
    val searchPageCode: String = "",
    val pages: Map<String, PageState> = emptyMap(),
    val isBootstrapping: Boolean = true,
    val error: String? = null,
    val authExpired: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchQuery.Result> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val language: String = "en"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val apollo: ApolloClient,
    private val languageRepository: LanguageRepository,
    private val previewChannelHelper: PreviewChannelHelper,
    private val authRepository: tv.brunstad.app.auth.AuthRepository,
    private val npawManager: tv.brunstad.app.data.NpawManager,
    private val analyticsManager: tv.brunstad.app.data.AnalyticsManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private var searchJob: Job? = null
    private var homePageCode: String = ""
    private var resumeCount = 0

    init {
        _state.value = _state.value.copy(language = languageRepository.getLanguage())
        bootstrap()
        // Reload all pages when the content language changes
        viewModelScope.launch {
            languageRepository.languageChanged.collect {
                _state.value = _state.value.copy(language = languageRepository.getLanguage())
                reloadAllPages()
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val result = runCatching {
                apollo.query(GetApplicationQuery()).execute().dataOrThrow()
            }
            result.onFailure { e ->
                val tokenValid = authRepository.getValidAccessToken() != null
                _state.value = _state.value.copy(
                    isBootstrapping = false,
                    error = e.message,
                    authExpired = !tokenValid
                )
                return@launch
            }
            val data = result.getOrThrow()
            val app = data.application

            val items = buildList {
                app.page?.let { add(NavItem(it.code, "Home", NavIcon.HOME)) }
                app.searchPage?.let { add(NavItem(it.code, it.title ?: "Search", NavIcon.SEARCH)) }
                add(NavItem(MY_LIST_CODE, _state.value.myListTitle, NavIcon.BOOKMARK))
            }

            val firstCode = items.firstOrNull()?.code ?: ""
            val searchCode = items.find { it.icon == NavIcon.SEARCH }?.code ?: ""
            homePageCode = firstCode
            _state.value = _state.value.copy(
                navItems = items,
                selectedCode = firstCode,
                searchPageCode = searchCode,
                isBootstrapping = false
            )

            if (firstCode.isNotEmpty()) loadPage(firstCode)

            // Update NPAW + Rudderstack analytics with user info
            runCatching {
                apollo.query(tv.brunstad.app.graphql.GetMeQuery()).execute().dataOrThrow()
            }.onSuccess { data ->
                val userInfo = authRepository.fetchUserInfo()
                val anonymousId = data.me?.analytics?.anonymousId
                npawManager.updateUserOptions(
                    anonymousId = anonymousId,
                    sessionId = tv.brunstad.app.di.AppModule.sessionId,
                    ageGroup = userInfo.ageGroup
                )
                if (anonymousId != null) {
                    analyticsManager.identify(anonymousId, kotlinx.serialization.json.buildJsonObject {
                        put("tv", kotlinx.serialization.json.JsonPrimitive(true))
                        userInfo.ageGroup?.let { put("ageGroup", kotlinx.serialization.json.JsonPrimitive(it)) }
                        userInfo.country?.let { put("country", kotlinx.serialization.json.JsonPrimitive(it)) }
                        userInfo.churchId?.let { put("churchId", kotlinx.serialization.json.JsonPrimitive(it)) }
                        userInfo.gender?.let { put("gender", kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                }
            }
        }
    }

    /** Called each time HomeScreen becomes visible. Skips the initial open; refreshes on return. */
    fun onScreenResumed() {
        resumeCount++
        if (resumeCount == 1) return  // initial open — bootstrap already loading
        val code = _state.value.selectedCode
        if (code.isNotEmpty() && code != MY_LIST_CODE) loadPage(code, silent = true)
    }

    fun selectPage(code: String) {
        _state.value = _state.value.copy(selectedCode = code, searchQuery = "", searchResults = emptyList())
        if (code != MY_LIST_CODE && _state.value.pages[code] == null) loadPage(code)
        analyticsManager.screen(code)
    }

    fun onSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.value = _state.value.copy(isSearching = true, searchError = null)
            val searchStart = System.currentTimeMillis()
            val response = runCatching {
                apollo.query(
                    SearchQuery(
                        queryString = query,
                        first = Optional.present(20),
                        offset = Optional.absent()
                    )
                ).execute()
            }.getOrElse { e ->
                _state.value = _state.value.copy(isSearching = false, searchError = "Network error: ${e.message}")
                return@launch
            }
            val searchLatency = (System.currentTimeMillis() - searchStart) / 1000.0
            val data = response.data
            if (data != null) {
                _state.value = _state.value.copy(
                    searchResults = data.search.result,
                    isSearching = false,
                    searchError = null
                )
                analyticsManager.trackSearchPerformed(
                    searchText = query,
                    searchLatency = searchLatency,
                    searchResultCount = data.search.result.size
                )
            } else {
                val errors = response.errors?.joinToString("; ") { it.message } ?: "Unknown error"
                _state.value = _state.value.copy(isSearching = false, searchError = errors)
            }
        }
    }

    /** Called when the content language changes — clears cached pages and reloads the current one. */
    private fun reloadAllPages() {
        _state.value = _state.value.copy(pages = emptyMap())
        val currentCode = _state.value.selectedCode
        if (currentCode.isNotEmpty()) loadPage(currentCode)
    }

    private fun loadPage(code: String, silent: Boolean = false) {
        if (!silent) {
            _state.value = _state.value.copy(
                pages = _state.value.pages + (code to PageState(isLoading = true))
            )
        }
        viewModelScope.launch {
            runCatching {
                apollo.query(
                    GetPageQuery(
                        code = code,
                        first = Optional.present(50),
                        offset = Optional.present(0)
                    )
                ).execute().dataOrThrow()
            }.onSuccess { data ->
                val allSections = data.page?.sections?.items ?: emptyList()
                // Filter out sections where metadata.myList == true (shown in nav instead)
                val sections = allSections.filter { !it.isMyListSection() }
                _state.value = _state.value.copy(
                    pages = _state.value.pages + (code to PageState(
                        sections = sections,
                        pageTitle = data.page?.title,
                        isLoading = false
                    ))
                )
                // Extract the My List section title for the nav item
                if (code == homePageCode) {
                    // Populate the Google TV preview channel with featured episodes
                    val featuredSection = allSections.firstOrNull { it.onFeaturedSection != null }
                    val previewPrograms = featuredSection?.onFeaturedSection?.items?.items
                        ?.mapNotNull { si ->
                            val ep = si.item?.onEpisode ?: return@mapNotNull null
                            PreviewProgramData(
                                episodeId = ep.id,
                                title = si.title,
                                showTitle = ep.season?.show?.title,
                                description = si.description?.takeIf { it.isNotBlank() },
                                imageUrl = si.image,
                                durationMs = ep.duration?.let { it * 1000L }
                            )
                        } ?: emptyList()
                    if (previewPrograms.isNotEmpty()) {
                        viewModelScope.launch { previewChannelHelper.updateChannel(previewPrograms) }
                    }
                    val myListSection = allSections.firstOrNull { it.isMyListSection() }
                    myListSection?.title?.let { apiTitle ->
                        val displayTitle = if (languageRepository.getLanguage() == "en") "Watchlist" else apiTitle
                        val updatedNavItems = _state.value.navItems.map { item ->
                            if (item.code == MY_LIST_CODE) item.copy(title = displayTitle) else item
                        }
                        _state.value = _state.value.copy(
                            myListTitle = displayTitle,
                            navItems = updatedNavItems
                        )
                    }
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    pages = _state.value.pages + (code to PageState(isLoading = false, error = e.message))
                )
            }
        }
    }

    fun trackSectionClicked(
        sectionId: String,
        sectionName: String,
        sectionPosition: Int,
        sectionType: String,
        elementPosition: Int,
        elementType: String,
        elementId: String,
        elementName: String
    ) {
        analyticsManager.trackSectionClicked(
            sectionId = sectionId,
            sectionName = sectionName,
            sectionPosition = sectionPosition,
            sectionType = sectionType,
            elementPosition = elementPosition,
            elementType = elementType,
            elementId = elementId,
            elementName = elementName,
            pageCode = _state.value.selectedCode
        )
    }

    fun trackSearchResultClicked(
        elementPosition: Int,
        elementType: String,
        elementId: String,
        group: String
    ) {
        analyticsManager.trackSearchResultClicked(
            searchText = _state.value.searchQuery,
            elementPosition = elementPosition,
            elementType = elementType,
            elementId = elementId,
            group = group
        )
    }
}
