package com.lalilu.lmedia.source.mediastore

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.*
import com.lalilu.lmedia.source.MediaSource
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
            "${MediaStore.Audio.Media.SIZE} >= 10 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
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

    override suspend fun scan(): Snapshot = withContext(Dispatchers.IO) {
        val cr = context.applicationContext.contentResolver
        val cursor = cr.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, BASE_SELECTOR, null, BASE_SORT_ORDER
        ) ?: return@withContext Snapshot.Empty

        val audios = cursor.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns._ID)
            val titleIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE)
            val sizeIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.SIZE)
            val dateAddedIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.DATE_ADDED)
            val dateModifiedIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.DATE_MODIFIED)
            val mimeTypeIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.MIME_TYPE)
            val durationIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION)
            val yearIdx = c.getColumnIndex(MediaStore.Audio.AudioColumns.YEAR)

            val list = mutableListOf<LAudio>()
            while (c.moveToNext()) {
                ensureActive()

                val id = c.getLong(idIdx)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                // 使用 Taglib FD 模式读取准确的元数据信息
                val metadata = runCatching {
                    cr.openFileDescriptor(uri, "r")
                        ?.use { fd -> Taglib.readMetadata(fd = fd.detachFd()) }
                }.getOrNull()

                // 从 MediaStore 游标构建 extra 信息
                val extras = mutableMapOf<String, String>()
                getStringOrNull(c, sizeIdx)?.let { extras["file_size"] = it }
                getStringOrNull(c, dateAddedIdx)?.let { extras["date_added"] = it }
                getStringOrNull(c, dateModifiedIdx)?.let { extras["date_modified"] = it }
                getStringOrNull(c, mimeTypeIdx)?.let { extras["content_type"] = it }
                getStringOrNull(c, durationIdx)?.let { extras["duration"] = it }
                getStringOrNull(c, yearIdx)?.let { extras["year"] = it }

                // 子类钩子：添加 API 级别特定的 extra
                onExtras(c, extras)

                val audio = with(source) {
                    buildAudio(id = uri.toString()) {
                        title(
                            metadata?.title?.takeIf { it.isNotBlank() }
                                ?: getStringOrNull(c, titleIdx)?.takeIf { it.isNotBlank() }
                                ?: "Unknown"
                        )
                        subtitle(
                            metadata?.title?.takeIf { it.isNotBlank() }
                                ?: getStringOrNull(c, titleIdx)?.takeIf { it.isNotBlank() }
                                ?: "Unknown"
                        )
                        metadata(metadata ?: Metadata.EMPTY)
                        extra(extras)
                        source(SourceItem.UriItem(uri))
                    }
                }
                list.add(audio)
            }
            list
        }

        audios.buildSnapshot()
    }

    /**
     * 子类可覆写此方法以添加 API 级别特定的 extra 列。
     */
    protected open fun onExtras(cursor: android.database.Cursor, extras: MutableMap<String, String>) {
    }

    protected fun getStringOrNull(cursor: android.database.Cursor, index: Int): String? {
        return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
    }
}
