package com.lalilu.lmedia.source.mediastore

import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.entity.Snapshot

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