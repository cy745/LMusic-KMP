package com.lalilu.lmedia.source.mediastore

import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.entity.Snapshot

open class MediaStoreScanner(
    private val context: Context
) : Scanner {

    companion object {

        @Suppress("inlinedApi")
        private const val VOLUME_EXTERNAL = MediaStore.VOLUME_EXTERNAL

        @Suppress("inlinedApi")
        private const val AUDIO_COLUMN_ALBUM_ARTIST = MediaStore.Audio.AudioColumns.ALBUM_ARTIST
        private const val BASE_SELECTOR =
            "${MediaStore.Audio.Media.SIZE} >= 10 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        private const val BASE_SORT_ORDER = "${MediaStore.Audio.Media._ID} DESC"
    }

    /**
     * 基础字段
     */
    private var idIndex = -1
    private var titleIndex = -1
    private var displayNameIndex = -1
    private var mimeTypeIndex = -1
    private var sizeIndex = -1
    private var dateAddedIndex = -1
    private var dateModifiedIndex = -1
    private var durationIndex = -1
    private var yearIndex = -1
    private var albumIndex = -1
    private var albumIdIndex = -1
    private var artistIndex = -1
    private var artistIdIndex = -1
    private var albumArtistIndex = -1

    private var genreIdIndex = -1
    private var genreNameIndex = -1

    open val projection: Array<String> = arrayOf(
        // These columns are guaranteed to work on all versions of android
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

    override fun scan(): Snapshot {
        val cursor = context.applicationContext.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, BASE_SELECTOR, null, BASE_SORT_ORDER
        )

        cursor?.close()
        return Snapshot.Empty
    }
}