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

/**
 * domain 与 code 必须**成对出现**才算匹配：`NSError.description` 的 `UserInfo` 里可能嵌套别的
 * domain/code，只按 `contains("Code=257")` 判断会把外层错误误判成内层类别。
 * 同一段文本出现多组成对模式时取**位置最靠前**的一组——`NSError.description` 的顶层
 * `Error Domain=… Code=…` 在最前，内层 UserInfo 在后。
 */
private val IOS_FAILURE_PATTERNS: List<Pair<PlaybackFailureReason, Regex>> = buildList {
    listOf(
        Regex("""Domain=NSCocoaErrorDomain Code=257\b"""),
        Regex("""NSCocoaErrorDomain error 257\b"""),
    ).forEach { add(PlaybackFailureReason.PermissionDenied to it) }
    listOf(
        Regex("""Domain=NSCocoaErrorDomain Code=260\b"""),
        Regex("""NSCocoaErrorDomain error 260\b"""),
    ).forEach { add(PlaybackFailureReason.FileMissing to it) }
    // 明确的连接类错误：超时、找不到主机、无法连接、连接中断、未联网。
    listOf(1001, 1003, 1004, 1005, 1009).forEach { code ->
        add(PlaybackFailureReason.Network to Regex("""Domain=NSURLErrorDomain Code=-?$code\b"""))
        add(PlaybackFailureReason.Network to Regex("""NSURLErrorDomain error -?$code\b"""))
    }
    // AVAudioPlayer 在播放中途报的解码失败（`onDecodeErrorDidOccur`）：数据读进来了但解不开，
    // 对应"文件可能已损坏"。这个标记由 AVAudioPlayerEngine 自己构造，是稳定可用的判据。
    add(PlaybackFailureReason.Decode to Regex("""AVAudioPlayerDecodeError"""))
    // 播放加载路径上的 OSStatus 失败意味着数据无法解码（AVAudioPlayer 构造/解码失败等）。
    listOf(
        Regex("""Domain=NSOSStatusErrorDomain Code="""),
        Regex("""OSStatus error """),
    ).forEach { add(PlaybackFailureReason.UnsupportedFormat to it) }
}

/** 只识别 Apple 文档中语义明确的 domain+code 对，其他文本返回 null 交给调用方按 Unknown 处理。 */
internal fun iosFailureReasonFromDescription(description: String): PlaybackFailureReason? =
    IOS_FAILURE_PATTERNS
        .mapNotNull { (reason, pattern) -> pattern.find(description)?.range?.first?.let { it to reason } }
        .minByOrNull { it.first }
        ?.second

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
