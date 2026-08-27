package com.lalilu.lmedia.source.mediastore

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.entity.toAudioExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

open class MediaStoreScanner(
    private val source: MediaSource,
    private val context: Context
) : Scanner {

    companion object {
        @Suppress("inlinedApi")
        private const val AUDIO_COLUMN_ALBUM_ARTIST = MediaStore.Audio.AudioColumns.ALBUM_ARTIST
        private const val BASE_SELECTOR =
            "${MediaStore.Audio.Media.SIZE} >= 10 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        private const val BASE_SORT_ORDER = "${MediaStore.Audio.Media._ID} DESC"
    }

    open val projection: Array<String> = arrayOf(
        MediaStore.Audio.AudioColumns._ID,
        MediaStore.Audio.AudioColumns.TITLE,
        MediaStore.Audio.AudioColumns.DISPLAY_NAME,
        MediaStore.Audio.AudioColumns.MIME_TYPE,
        MediaStore.Audio.AudioColumns.SIZE,
        MediaStore.Audio.AudioColumns.DATE_ADDED,
        MediaStore.Audio.AudioColumns.DATE_MODIFIED,
        MediaStore.Audio.AudioColumns.DURATION,
        MediaStore.Audio.AudioColumns.YEAR,
        MediaStore.Audio.AudioColumns.ALBUM,
        MediaStore.Audio.AudioColumns.ALBUM_ID,
        MediaStore.Audio.AudioColumns.ARTIST,
        MediaStore.Audio.AudioColumns.ARTIST_ID,
        AUDIO_COLUMN_ALBUM_ARTIST
    )

    override suspend fun scan(minDurationMillis: Long): List<LAudio> = withContext(Dispatchers.IO) {
        val cr = context.applicationContext.contentResolver
        val cursor = cr.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            BASE_SELECTOR,
            arrayOf(minDurationMillis.coerceAtLeast(0L).toString()),
            BASE_SORT_ORDER,
        ) ?: return@withContext emptyList()

        val audios = cursor.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns._ID)
            val titleIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE)
            val sizeIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.SIZE)
            val dateAddedIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.DATE_ADDED)
            val dateModifiedIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.DATE_MODIFIED)
            val mimeTypeIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.MIME_TYPE)
            val durationIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION)
            val yearIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.YEAR)
            val albumIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM)
            val albumIdIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID)
            val artistIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST)
            val artistIdIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST_ID)
            val albumArtistIdx = c.getColumnIndex(AUDIO_COLUMN_ALBUM_ARTIST)

            val list = mutableListOf<LAudio>()
            while (c.moveToNext()) {
                ensureActive()

                val id = c.getLong(idIdx)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                val metadata = runCatching {
                    cr.openFileDescriptor(uri, "r")
                        ?.use { fd -> Taglib.readMetadata(fd = fd.detachFd()) }
                }.getOrNull()

                val extras = mutableMapOf<String, String>().apply {
                    put("uri", uri.toString())
                    getStringOrNull(c, sizeIdx)?.let { put("file_size", it) }
                    getStringOrNull(c, dateAddedIdx)?.let { put(LAudioExtraKeys.DateAdded, it) }
                    getStringOrNull(c, dateModifiedIdx)?.let { put(LAudioExtraKeys.DateModified, it) }
                    getStringOrNull(c, mimeTypeIdx)?.let { put("content_type", it) }
                    getStringOrNull(c, durationIdx)?.let { put(LAudioExtraKeys.Duration, it) }
                    getStringOrNull(c, yearIdx)?.let { put(LAudioExtraKeys.Date, it) }
                    getStringOrNull(c, albumIdx)?.let { put(LAudioExtraKeys.AlbumName, it) }
                    getStringOrNull(c, artistIdx)?.let { put(LAudioExtraKeys.ArtistName, it) }
                    getStringOrNull(c, albumArtistIdx)?.let { put(LAudioExtraKeys.AlbumArtist, it) }
                }

                // Hook for API-level specific extras
                onExtras(c, extras)

                val audio = LAudio(
                    id = "${LAudio.ID_PREFIX}$uri",
                    title = (
                        metadata?.title?.takeIf { it.isNotBlank() }
                            ?: getStringOrNull(c, titleIdx)?.takeIf { it.isNotBlank() }
                            ?: "Unknown"
                        ),
                    subtitle = (
                        metadata?.artist?.takeIf { it.isNotBlank() }
                            ?: getStringOrNull(c, artistIdx)?.takeIf { it.isNotBlank() }
                            ?: "Unknown"
                        ),
                    mediaSourceName = source.name,
                    // Metadata 只在扫描阶段存在，歌曲长期保存统一使用 extra。
                    extra = metadata?.toAudioExtra(
                        sourceExtra = extras,
                        artistId = getStringOrNull(c, artistIdIdx),
                        albumId = getStringOrNull(c, albumIdIdx),
                    ) ?: extras.apply {
                        getStringOrNull(c, artistIdIdx)?.let { put(LAudioExtraKeys.ArtistId, it) }
                        getStringOrNull(c, albumIdIdx)?.let { put(LAudioExtraKeys.AlbumId, it) }
                    }
                )
                list.add(audio)
            }
            list
        }

        audios
    }

    /**
     * Subclasses can override to add API-level specific extra columns.
     */
    protected open fun onExtras(cursor: android.database.Cursor, extras: MutableMap<String, String>) {
    }

    protected fun getStringOrNull(cursor: android.database.Cursor, index: Int): String? {
        return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
    }
}
