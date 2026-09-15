package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.source.AudioMediaMissingException
import kotlinx.coroutines.CancellationException

/**
 * iOS 侧的播放失败分类。
 *
 * AVFoundation 的错误以 `NSError.description` 文本进入异常 message 或 `PlaybackEngineState.error`，
 * 形如 `Error Domain=NSURLErrorDomain Code=-1009 "..."`。这里**只**识别明确的 error domain + code，
 * 并且只持久化类别，不落库任何原文（描述里可能带有 URL、本地路径或服务端凭据）。
 *
 * 刻意不把整个 `NSURLErrorDomain` 判成网络问题：AVFoundation 对不存在的本地文件同样可能报
 * `NSURLErrorDomain Code=-1100`，那会被误导成"检查网络连接"。无法确定时返回 Unknown，
 * 由界面给出通用重试提示，而不是猜测一个可能错误的原因。
 */
internal fun classifyIosLoadFailure(error: Throwable): PlaybackFailureReason {
    val visited = mutableSetOf<Throwable>()
    var cause: Throwable? = error
    while (cause != null && visited.add(cause)) {
        when {
            cause is AudioMediaMissingException -> return PlaybackFailureReason.FileMissing
            else -> cause.message?.let { description ->
                iosFailureReasonFromDescription(description)?.let { return it }
            }
        }
        cause = cause.cause
    }
    return PlaybackFailureReason.Unknown
}

/** 仅识别 Apple 文档中语义明确的错误码，其他文本一律返回 null 交给调用方按 Unknown 处理。 */
internal fun iosFailureReasonFromDescription(description: String): PlaybackFailureReason? = when {
    description.contains("NSCocoaErrorDomain") && description.contains("Code=257") ->
        PlaybackFailureReason.PermissionDenied
    description.contains("NSCocoaErrorDomain") && description.contains("Code=260") ->
        PlaybackFailureReason.FileMissing
    description.contains("NSURLErrorDomain") && IOS_NETWORK_ERROR_CODES.any { description.contains("Code=$it") } ->
        PlaybackFailureReason.Network
    else -> null
}

/** 明确的连接类错误：超时、找不到主机、无法连接、连接中断、未联网。 */
private val IOS_NETWORK_ERROR_CODES = listOf(-1001, -1003, -1004, -1005, -1009)

/**
 * 只有仍属于同一首歌、所属来源已就绪、且不是取消的失败才写入记录。
 * 旧歌曲的迟到失败、来源本身还在加载、以及用户取消都不能算成这首歌播放失败。
 */
internal fun shouldRecordIosLoadFailure(
    playbackId: String?,
    ticketId: String,
    sourceReady: Boolean,
    error: Throwable,
): Boolean = playbackId != null &&
    playbackId == ticketId &&
    sourceReady &&
    error !is CancellationException
