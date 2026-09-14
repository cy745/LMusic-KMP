package com.lalilu.lmedia.data.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "playback_failure")
data class PlaybackFailureEntity(
    @PrimaryKey val playbackId: String,
    val reason: String,
    val occurredAtMillis: Long,
)
