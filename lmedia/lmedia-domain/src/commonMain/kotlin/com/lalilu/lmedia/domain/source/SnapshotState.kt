package com.lalilu.lmedia.domain.source

import kotlinx.serialization.Serializable

@Serializable
sealed interface SnapshotState {
    @Serializable
    data object Idle : SnapshotState

    @Serializable
    data object Success : SnapshotState

    @Serializable
    data class Loading(
        val message: String = "Loading...",
        val progress: Float = 0f,
    ) : SnapshotState

    @Serializable
    data class Error(
        val message: String = "Error"
    ) : SnapshotState
}
