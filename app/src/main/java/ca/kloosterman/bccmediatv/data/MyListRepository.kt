package tv.brunstad.app.data

import android.content.SharedPreferences
import android.util.Log
import tv.brunstad.app.graphql.AddEpisodeToMyListMutation
import tv.brunstad.app.graphql.AddShowToMyListMutation
import tv.brunstad.app.graphql.GetMyListQuery
import tv.brunstad.app.graphql.RemoveFromMyListMutation
import com.apollographql.apollo.ApolloClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class ShowBookmarkItem(val id: String, val title: String, val image: String?)

@Singleton
class MyListRepository @Inject constructor(
    private val apollo: ApolloClient,
    private val prefs: SharedPreferences
) {
    companion object {
        private const val SHOW_ENTRY_PREFIX = "show_bm_entry_"
        private const val SHOW_TITLE_PREFIX = "show_bm_title_"
        private const val SHOW_IMAGE_PREFIX = "show_bm_image_"
    }

    // Maps content ID (episode/show integer ID) -> entry UUID (needed for removal)
    private val _entryMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val entryMap: StateFlow<Map<String, String>> = _entryMap

    // Episode entries from the API (shows excluded — their data is blank from API)
    private val _entries = MutableStateFlow<List<GetMyListQuery.Item>>(emptyList())
    val entries: StateFlow<List<GetMyListQuery.Item>> = _entries

    // Show bookmarks stored locally since API returns blank show data in GetMyList
    private val _showBookmarks = MutableStateFlow<List<ShowBookmarkItem>>(emptyList())
    val showBookmarks: StateFlow<List<ShowBookmarkItem>> = _showBookmarks

    // Show entryIds from GetMyList that we can't map to a showId (API returns blank ids for shows)
    // Used as a recovery pool when addShow gets a duplicate-key error but we have no local prefs entry
    private val _unresolvedShowEntryIds = MutableStateFlow<List<String>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var loaded = false
    private val loadMutex = Mutex()

    init {
        loadShowBookmarksFromPrefs()
    }

    private fun loadShowBookmarksFromPrefs() {
        val all = prefs.all
        val showIds = all.keys
            .filter { it.startsWith(SHOW_ENTRY_PREFIX) }
            .map { it.removePrefix(SHOW_ENTRY_PREFIX) }
        val shows = showIds.mapNotNull { id ->
            val entryId = prefs.getString("$SHOW_ENTRY_PREFIX$id", null) ?: return@mapNotNull null
            val title = prefs.getString("$SHOW_TITLE_PREFIX$id", "") ?: ""
            val image = prefs.getString("$SHOW_IMAGE_PREFIX$id", null)
            ShowBookmarkItem(id, title, image)
        }
        _showBookmarks.value = shows
        // Seed entryMap with locally stored show bookmarks
        val showEntries = showIds.mapNotNull { id ->
            val entryId = prefs.getString("$SHOW_ENTRY_PREFIX$id", null) ?: return@mapNotNull null
            id to entryId
        }.toMap()
        _entryMap.value = _entryMap.value + showEntries
    }

    suspend fun load(force: Boolean = false) {
        loadMutex.withLock {
            if (loaded && !force) return
            _isLoading.value = true
            _error.value = null
            runCatching {
                apollo.query(GetMyListQuery()).execute().dataOrThrow()
            }.onSuccess { data ->
                val items = data.myList.entries.items
                // Filter out show entries — the API returns blank id/title/image for shows
                val episodeItems = items.filter { entry ->
                    val ep = entry.item?.onEpisode
                    ep != null && ep.id.isNotBlank()
                }
                _entries.value = episodeItems
                val episodeEntries = episodeItems.mapNotNull { entry ->
                    val epId = entry.item?.onEpisode?.id
                    epId?.let { it to entry.id }
                }.toMap()
                // Merge episode entries with locally-stored show entries
                val showEntries = _showBookmarks.value.mapNotNull { show ->
                    prefs.getString("$SHOW_ENTRY_PREFIX${show.id}", null)?.let { show.id to it }
                }.toMap()
                _entryMap.value = episodeEntries + showEntries
                // Track show entryIds that we can't resolve to a showId (for duplicate-key recovery)
                val knownShowEntryIds = showEntries.values.toSet()
                _unresolvedShowEntryIds.value = items
                    .filter { it.item?.__typename == "Show" && it.id !in knownShowEntryIds }
                    .map { it.id }
                loaded = true
            }.onFailure { e ->
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }

    fun isInList(contentId: String) = _entryMap.value.containsKey(contentId)

    suspend fun addEpisode(episodeId: String): Boolean {
        val response = apollo.mutation(AddEpisodeToMyListMutation(episodeId = episodeId)).execute()
        if (response.errors?.any { it.message.contains("duplicate key", ignoreCase = true) } == true) {
            load(force = true)
            return true
        }
        return runCatching {
            val entryId = response.dataOrThrow().addEpisodeToMyList.entryId
            _entryMap.value = _entryMap.value + (episodeId to entryId)
            load(force = true)
            true
        }.onFailure { e ->
            Log.e("MyList", "addEpisode FAILED for $episodeId: ${e.message}")
        }.getOrDefault(false)
    }

    suspend fun addShow(showId: String, title: String, image: String?): Boolean {
        val response = apollo.mutation(AddShowToMyListMutation(showId = showId)).execute()
        val isDuplicate = response.errors?.any { it.message.contains("duplicate key", ignoreCase = true) } == true
        val entryId = if (isDuplicate) {
            // Already on server — check local prefs first, then fall back to any unresolved show entry
            prefs.getString("$SHOW_ENTRY_PREFIX$showId", null)
                ?: _unresolvedShowEntryIds.value.firstOrNull()
        } else {
            runCatching { response.dataOrThrow().addShowToMyList.entryId }.getOrNull()
        }
        if (entryId == null) {
            Log.e("MyList", "addShow FAILED for $showId: no entryId")
            return false
        }
        val editor = prefs.edit()
            .putString("$SHOW_ENTRY_PREFIX$showId", entryId)
            .putString("$SHOW_TITLE_PREFIX$showId", title)
        if (image != null) editor.putString("$SHOW_IMAGE_PREFIX$showId", image)
        else editor.remove("$SHOW_IMAGE_PREFIX$showId")
        editor.apply()
        _entryMap.value = _entryMap.value + (showId to entryId)
        val notInList = _showBookmarks.value.none { it.id == showId }
        if (notInList) _showBookmarks.value = _showBookmarks.value + ShowBookmarkItem(showId, title, image)
        return true
    }

    suspend fun remove(contentId: String): Boolean {
        val entryId = _entryMap.value[contentId] ?: return false
        // Optimistic update
        _entryMap.value = _entryMap.value - contentId
        _entries.value = _entries.value.filter { it.id != entryId }
        _showBookmarks.value = _showBookmarks.value.filter { it.id != contentId }
        prefs.edit()
            .remove("$SHOW_ENTRY_PREFIX$contentId")
            .remove("$SHOW_TITLE_PREFIX$contentId")
            .remove("$SHOW_IMAGE_PREFIX$contentId")
            .apply()
        return runCatching {
            val response = apollo.mutation(RemoveFromMyListMutation(entryId = entryId)).execute()
            response.dataOrThrow()
            true
        }.onFailure { e ->
            Log.e("MyList", "remove FAILED: ${e.message}", e)
            // Roll back
            loadShowBookmarksFromPrefs()
            load(force = true)
        }.getOrDefault(false)
    }
}
