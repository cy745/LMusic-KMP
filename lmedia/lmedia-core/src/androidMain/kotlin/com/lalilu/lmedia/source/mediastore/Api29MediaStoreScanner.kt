package com.lalilu.lmedia.source.mediastore

import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.entity.Snapshot


open class Api29MediaStoreScanner(
    private val context: Context
) : Api21MediaStoreScanner(context) {
    private var trackIndex = -1

    override val projection: Array<String> = super.projection + arrayOf(
        MediaStore.Audio.AudioColumns.TRACK
    )

    override fun scan(): Snapshot {
        return super.scan()
    }
}