package ca.kloosterman.bccmediatv.data

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import ca.kloosterman.bccmediatv.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PreviewProgramData(
    val episodeId: String,
    val title: String,
    val showTitle: String?,
    val description: String?,
    val imageUrl: String?,
    val durationMs: Long?
)

@Singleton
class PreviewChannelHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("preview_channel", Context.MODE_PRIVATE)

    fun updateChannel(programs: List<PreviewProgramData>) {
        if (programs.isEmpty()) return
        runCatching {
            val channelId = getOrCreateChannel() ?: return
            replacePrograms(channelId, programs)
            // Request that Google TV make this channel visible on the home screen.
            // On first run this shows the user a prompt; subsequent calls are no-ops.
            TvContractCompat.requestChannelBrowsable(context, channelId)
            Log.d("PreviewChannel", "Updated channel $channelId with ${programs.size} programs")
        }.onFailure { e ->
            Log.e("PreviewChannel", "Failed to update channel", e)
        }
    }

    private fun getOrCreateChannel(): Long? {
        val savedId = prefs.getLong("channel_id", -1L)
        if (savedId != -1L && channelExists(savedId)) return savedId

        val logo = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.bcc_media_banner_320x180)
        }.getOrNull()

        val builder = PreviewChannel.Builder()
            .setDisplayName(context.getString(R.string.app_name))
            .setAppLinkIntentUri(Uri.parse("bccmediatv://home"))
        logo?.let { builder.setLogo(it) }

        val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, builder.build().toContentValues())
            ?: run {
                Log.e("PreviewChannel", "Failed to insert channel")
                return null
            }
        val id = ContentUris.parseId(uri)
        prefs.edit().putLong("channel_id", id).apply()
        Log.d("PreviewChannel", "Created channel id=$id")
        return id
    }

    private fun channelExists(id: Long): Boolean =
        resolver.query(
            TvContractCompat.buildChannelUri(id),
            arrayOf(TvContractCompat.Channels._ID),
            null, null, null
        )?.use { it.count > 0 } ?: false

    private fun replacePrograms(channelId: Long, programs: List<PreviewProgramData>) {
        resolver.delete(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            null, null
        )
        programs.forEach { program ->
            val builder = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE)
                .setIntentUri(Uri.parse("bccmediatv://episode/${program.episodeId}"))
            if (program.showTitle != null) {
                builder.setTitle(program.showTitle).setEpisodeTitle(program.title)
            } else {
                builder.setTitle(program.title)
            }
            program.description?.let { builder.setDescription(it) }
            program.imageUrl?.let { builder.setPosterArtUri(Uri.parse(it)) }
            program.durationMs?.let { builder.setDurationMillis(it.toInt()) }
            resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, builder.build().toContentValues())
        }
    }
}
