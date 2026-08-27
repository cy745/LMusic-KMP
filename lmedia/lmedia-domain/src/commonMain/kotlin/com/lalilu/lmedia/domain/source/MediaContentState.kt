package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.flow.first

/**
 * 数据源读取媒体内容（音频、封面、歌词）的就绪状态。
 *
 * [generation] 只在一次新的完整加载成功后递增，普通扫描进度不会改变它。消费者可以据此重新发起
 * 已经完成或失败的内容请求，同时不会被高频进度更新反复触发。
 */
data class MediaContentState(
    val availability: MediaContentAvailability = MediaContentAvailability.Unavailable(
        reason = "Not initialized",
    ),
    val generation: Long = 0L,
) {
    val isReady: Boolean
        get() = availability is MediaContentAvailability.Ready
}

sealed interface MediaContentAvailability {
    data object Preparing : MediaContentAvailability
    data object Ready : MediaContentAvailability
    data class Unavailable(val reason: String) : MediaContentAvailability
}

/** 挂起等待所属数据源可读取内容；等待过程会随调用方协程一起取消。 */
suspend fun MediaSource.awaitContentReady(): MediaContentState =
    contentState.first { it.isReady }
