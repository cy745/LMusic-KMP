package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable

/** Persist categories, not exception messages that can contain server credentials or file paths. */
@Serializable
enum class PlaybackFailureReason(val displayMessage: String) {
    FileMissing("找不到音频文件"),
    PermissionDenied("没有读取音频的权限"),
    Network("无法读取网络音频，请检查连接后重试"),
    UnsupportedFormat("不支持此音频格式或文件已损坏"),
    Decode("音频解码失败，文件可能已损坏"),
    Unknown("播放失败，请重试"),
}

@Serializable
data class PlaybackFailure(
    val reason: PlaybackFailureReason,
    val occurredAtMillis: Long,
)

/** Source readiness takes precedence over a remembered song failure. */
sealed interface AudioPlaybackPresentation {
    data object SourceNotReady : AudioPlaybackPresentation
    data object Available : AudioPlaybackPresentation
    data class Failed(val reason: PlaybackFailureReason) : AudioPlaybackPresentation
}

fun audioPlaybackPresentation(
    sourceReady: Boolean,
    available: Boolean,
    failure: PlaybackFailure?,
): AudioPlaybackPresentation = when {
    !sourceReady -> AudioPlaybackPresentation.SourceNotReady
    failure != null -> AudioPlaybackPresentation.Failed(failure.reason)
    !available -> AudioPlaybackPresentation.Failed(PlaybackFailureReason.FileMissing)
    else -> AudioPlaybackPresentation.Available
}
