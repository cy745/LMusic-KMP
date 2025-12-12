package com.lalilu.lmedia.source.mediastore

import android.content.Context
import android.provider.MediaStore
import com.lalilu.lmedia.entity.Snapshot

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