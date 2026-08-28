package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout

/**
 * 数据源读取媒体内容（音频、封面、歌词）的就绪状态。
 *
 * [generation] 只在一次新的完整加载成功后递增，普通扫描进度不会改变它。消费者可以据此重新发起
 * 已经完成或失败的内容请求，同时不会被高频进度更新反复触发。
 */
data class MediaContentState(
    val availability: MediaContentAvailability = MediaContentAvailability.Uninitialized,
    val generation: Long = 0L,
) {
    val isReady: Boolean
        get() = availability is MediaContentAvailability.Ready
}

sealed interface MediaContentAvailability {
    data object Uninitialized : MediaContentAvailability
    data object Preparing : MediaContentAvailability
    data object Ready : MediaContentAvailability
    data class Unavailable(val reason: String) : MediaContentAvailability
}

/**
 * 独立于扫描任务的媒体读取能力状态容器。
 *
 * 普通刷新开始时可以继续保留既有 Ready 状态；只有配置、权限或连接确实失效时，数据源才应
 * 主动标记为 Unavailable。generation 由数据源在可能改变播放、封面或歌词读取结果时递增。
 */
class MediaContentStateStore {
    private val mutableState = MutableStateFlow(MediaContentState())

    val state: StateFlow<MediaContentState> = mutableState.asStateFlow()

    fun preparing(preserveReady: Boolean = true) {
        mutableState.update { current ->
            if (preserveReady && current.isReady) current
            else current.copy(availability = MediaContentAvailability.Preparing)
        }
    }

    fun ready(contentChanged: Boolean = true) {
        mutableState.update { current ->
            current.copy(
                availability = MediaContentAvailability.Ready,
                generation = current.generation + if (contentChanged) 1 else 0,
            )
        }
    }

    fun unavailable(reason: String, preserveReady: Boolean = false) {
        mutableState.update { current ->
            if (preserveReady && current.isReady) current
            else current.copy(availability = MediaContentAvailability.Unavailable(reason))
        }
    }
}

class MediaContentUnavailableException(message: String) : IllegalStateException(message)

/** 挂起等待所属数据源可读取内容；等待过程会随调用方协程一起取消。 */
suspend fun MediaSource.awaitContentReady(): MediaContentState =
    contentState.first { it.isReady }

/** 播放路径使用的有限等待：明确不可用时立即失败，初始化或准备阶段超时后终止。 */
suspend fun MediaSource.requireContentReady(timeoutMillis: Long = 15_000L): MediaContentState =
    withTimeout(timeoutMillis) {
        contentState.first { state ->
            when (val availability = state.availability) {
                MediaContentAvailability.Ready -> true
                is MediaContentAvailability.Unavailable -> throw MediaContentUnavailableException(
                    "Media source '$name' is unavailable: ${availability.reason}"
                )
                MediaContentAvailability.Preparing,
                MediaContentAvailability.Uninitialized -> false
            }
        }
    }
