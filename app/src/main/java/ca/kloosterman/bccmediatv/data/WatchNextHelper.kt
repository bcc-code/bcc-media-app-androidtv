package tv.brunstad.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchNextHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver = context.contentResolver

    /** Insert or update a Continue Watching entry for the given episode. */
    fun upsert(
        episodeId: String,
        title: String,
        description: String?,
        imageUrl: String?,
        durationMs: Long?,
        positionMs: Long,
        showTitle: String?
    ) {
        runCatching {
            val existingId = findExistingId(episodeId)
            val builder = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setTitle(title)
                .setInternalProviderId(episodeId)
                .setLastPlaybackPositionMillis(positionMs.toInt())
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setIntentUri(Uri.parse("bccmediatv://episode/$episodeId"))

            showTitle?.let { builder.setEpisodeTitle(title).setTitle(it) }
            description?.let { builder.setDescription(it) }
            imageUrl?.let { builder.setPosterArtUri(Uri.parse(it)) }
            durationMs?.let { builder.setDurationMillis(it.toInt()) }

            val values = builder.build().toContentValues()
            if (existingId != null) {
                val uri = TvContractCompat.buildWatchNextProgramUri(existingId)
                val rows = resolver.update(uri, values, null, null)
                Log.d("WatchNext", "upsert update episodeId=$episodeId rows=$rows")
            } else {
                val uri = resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
                Log.d("WatchNext", "upsert insert episodeId=$episodeId result=$uri")
            }
        }.onFailure { e ->
            Log.e("WatchNext", "upsert failed for episodeId=$episodeId", e)
        }
    }

    /** Remove the Continue Watching entry for the given episode (e.g. when fully watched). */
    fun remove(episodeId: String) {
        runCatching {
            val existingId = findExistingId(episodeId) ?: run {
                Log.d("WatchNext", "remove: no entry found for episodeId=$episodeId")
                return@runCatching
            }
            val uri = TvContractCompat.buildWatchNextProgramUri(existingId)
            val rows = resolver.delete(uri, null, null)
            Log.d("WatchNext", "remove episodeId=$episodeId rows=$rows")
        }.onFailure { e ->
            Log.e("WatchNext", "remove failed for episodeId=$episodeId", e)
        }
    }

    private fun findExistingId(episodeId: String): Long? {
        val cursor = resolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            arrayOf(TvContractCompat.WatchNextPrograms._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            null, null, null
        ) ?: return null
        return cursor.use { c ->
            val idCol = c.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
            val providerCol = c.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            while (c.moveToNext()) {
                if (c.getString(providerCol) == episodeId) {
                    return@use c.getLong(idCol)
                }
            }
            null
        }
    }
}
