package com.lalilu.lmedia.source.mediastore

import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.domain.source.MediaSource

open class Api21MediaStoreScanner(
    private val source: MediaSource,
    private val context: Context
) : MediaStoreScanner(source, context) {

    override val projection: Array<String> = super.projection + arrayOf(
        MediaStore.Audio.AudioColumns.TRACK,
        MediaStore.Audio.AudioColumns.DATA
    )

    override fun onExtras(cursor: android.database.Cursor, extras: MutableMap<String, String>) {
        val trackIdx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.TRACK)
        getStringOrNull(cursor, trackIdx)?.let { extras["track"] = it }
    }
}
