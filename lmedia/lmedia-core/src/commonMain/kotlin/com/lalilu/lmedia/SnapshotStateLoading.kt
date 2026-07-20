package com.lalilu.lmedia

import androidx.compose.runtime.mutableStateOf
import com.lalilu.lmedia.domain.source.SnapshotState

class SnapshotStateLoading(
    message: String, progress: Float
) : SnapshotState.Loading(message, progress) {
    val messageState = mutableStateOf(super.message)
    val progressState = mutableStateOf(super.progress)

    override val message: String
        get() = messageState.value

    override val progress: Float
        get() = progressState.value
}