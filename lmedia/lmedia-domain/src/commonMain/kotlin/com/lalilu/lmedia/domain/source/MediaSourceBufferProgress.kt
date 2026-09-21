package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.flow.Flow

/**
 * 一段已缓冲的区间，用整首的比例表示（0f..1f）。
 *
 * 用"区间列表"而不是单一比例：缓存可以是不连续的（播放器跳过的部分可能还没下，而跳转之后的
 * 那一段已经下好了），单一比例在这种情况下会把"可播到哪里"画错。
 *
 * 比例按**字节**算而不是时间：缓存本来就是按字节落的。对 FLAC 这类近恒定码率的音频两者基本
 * 一致；UI 直接把它当作进度条上的位置使用。
 */
data class BufferedRange(
    val startFraction: Float,
    val endFraction: Float,
) {
    init {
        require(startFraction <= endFraction) { "Range start must not exceed end: $startFraction..$endFraction" }
    }

    val isEmpty: Boolean get() = endFraction <= startFraction
}

/**
 * 走本地缓存/回环代理的媒体源可以额外上报"当前这首缓冲到哪了"。
 *
 * 为什么不让播放器自己报：经代理播放时字节是**数据源在下载**的，播放器对渐进式 HTTP 的
 * 内部缓冲报告常常是 0 或远小于真实进度；缓存覆盖率才是"还有多久能听"的准确信号。
 *
 * 不实现这个接口的源（本地文件、纯流式源）UI 就当它是"无需缓冲"。
 */
interface MediaSourceBufferProgress {
    /**
     * 指定歌曲已缓冲的区间（有序、互不相邻）。空列表表示"现在没有可显示的缓冲信息"——数据源
     * 不上报、远端没给总长度、或还没开始下载都走这一条，UI 因此不必区分三种情况。
     */
    fun bufferProgress(audioId: String): Flow<List<BufferedRange>>
}
