package com.lalilu.lmedia.source.mediastore

import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.source.MediaSource

open class Api29MediaStoreScanner(
    private val source: MediaSource,
    private val context: Context
) : Api21MediaStoreScanner(source, context) {

    override val projection: Array<String> = super.projection + arrayOf(
        MediaStore.Audio.AudioColumns.TRACK
    )
}
