package tv.brunstad.app.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import tv.brunstad.app.graphql.SearchQuery

class SearchSuggestionProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SuggestionEntryPoint {
        fun apolloClient(): ApolloClient
    }

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, MATCH_QUERY)
        addURI(AUTHORITY, "${SearchManager.SUGGEST_URI_PATH_QUERY}/*", MATCH_QUERY)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        if (matcher.match(uri) != MATCH_QUERY) return cursor

        val query = uri.lastPathSegment
            ?.takeIf { it != SearchManager.SUGGEST_URI_PATH_QUERY }
            ?: return cursor
        if (query.isBlank()) return cursor

        val ctx = context ?: return cursor
        val apollo = EntryPointAccessors
            .fromApplication(ctx.applicationContext, SuggestionEntryPoint::class.java)
            .apolloClient()

        val results = runCatching {
            runBlocking {
                apollo.query(
                    SearchQuery(
                        queryString = query,
                        first = Optional.present(20),
                        offset = Optional.absent()
                    )
                ).execute().data?.search?.result.orEmpty()
            }
        }.getOrElse { return cursor }

        results.forEach { item ->
            // Only surface episodes — they have a working deep link.
            val episode = item.onEpisodeSearchItem ?: return@forEach
            val secondary = listOfNotNull(episode.showTitle, episode.seasonTitle)
                .joinToString(" · ")
                .ifBlank { item.description.orEmpty() }
            cursor.addRow(arrayOf(
                item.id,
                item.title,
                secondary,
                item.image,
                "tv.brunstad.app://episode/${item.id}"
            ))
        }
        return cursor
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val AUTHORITY = "tv.brunstad.app.search"
        private const val MATCH_QUERY = 1
        private val COLUMNS = arrayOf(
            "_id",
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA
        )
    }
}
