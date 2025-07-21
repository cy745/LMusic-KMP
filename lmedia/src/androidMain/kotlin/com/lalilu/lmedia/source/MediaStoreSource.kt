package com.lalilu.lmedia.source

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single
class MediaStoreSource(
    private val context: Application
) : MediaSource {
    override val name: String = "MediaStore"
    private val scanner = when {
        Build.VERSION.SDK_INT >= 30 -> Api30MediaStoreScanner(context)
        Build.VERSION.SDK_INT >= 29 -> Api29MediaStoreScanner(context)
        Build.VERSION.SDK_INT >= 21 -> Api21MediaStoreScanner(context)
        else -> MediaStoreScanner(context)
    }

    override fun source(): Flow<Snapshot> {
        val eventFlow = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    launch { send(System.currentTimeMillis()) }
                }
            }

            context.applicationContext.contentResolver
                .registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)

            invokeOnClose {
                context.applicationContext.contentResolver
                    .unregisterContentObserver(observer)
            }
        }

        return eventFlow.mapLatest { updateTime ->
            scanner.scan()
        }
    }
}

interface Scanner {
    fun scan(): Snapshot
}

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

open class Api21MediaStoreScanner(
    private val context: Context
) : MediaStoreScanner(context) {
    private var trackIndex = -1
    private var dataIndex = -1

    override val projection: Array<String> = super.projection + arrayOf(
        MediaStore.Audio.AudioColumns.TRACK,
        MediaStore.Audio.AudioColumns.DATA
    )

    override fun scan(): Snapshot {
        return super.scan()
    }
}

open class Api29MediaStoreScanner(
    private val context: Context
) : Api21MediaStoreScanner(context) {
    private var trackIndex = -1

    override val projection: Array<String>
        get() = super.projection + arrayOf(MediaStore.Audio.AudioColumns.TRACK)

    override fun scan(): Snapshot {
        return super.scan()
    }
}

open class Api30MediaStoreScanner(
    private val context: Context
) : Api29MediaStoreScanner(context) {
    private var trackIndex = -1
    private var discIndex = -1
    private var bitrateIndex = -1

    override val projection: Array<String> = super.projection + arrayOf(
        MediaStore.Audio.AudioColumns.CD_TRACK_NUMBER,
        MediaStore.Audio.AudioColumns.DISC_NUMBER,
        MediaStore.Audio.AudioColumns.BITRATE,
    )

    override fun scan(): Snapshot {
        return super.scan()
    }
}