package com.lalilu.lmedia.source.mediastore

import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.domain.source.MediaSource

open class Api30MediaStoreScanner(
    private val source: MediaSource,
    private val context: Context
) : Api29MediaStoreScanner(source, context) {

    override val projection: Array<String> = super.projection + arrayOf(
        MediaStore.Audio.AudioColumns.CD_TRACK_NUMBER,
        MediaStore.Audio.AudioColumns.DISC_NUMBER,
        MediaStore.Audio.AudioColumns.BITRATE,
    )

    override fun onExtras(cursor: android.database.Cursor, extras: MutableMap<String, String>) {
        super.onExtras(cursor, extras)

        // CD_TRACK_NUMBER is the non-deprecated replacement for TRACK on API 30+
        val trackIdx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.CD_TRACK_NUMBER)
        getStringOrNull(cursor, trackIdx)?.let { extras["track"] = it }

        val discIdx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DISC_NUMBER)
        getStringOrNull(cursor, discIdx)?.let { extras["disc"] = it }

        val bitrateIdx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.BITRATE)
        getStringOrNull(cursor, bitrateIdx)?.let { extras["bitrate"] = it }
    }
}