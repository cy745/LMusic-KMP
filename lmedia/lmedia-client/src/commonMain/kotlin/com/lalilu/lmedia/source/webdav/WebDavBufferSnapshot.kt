package com.lalilu.lmedia.source.webdav

import com.lalilu.lmedia.domain.source.BufferedRange
import com.lalilu.lmedia.stream.CachedRange

/**
 * "正在播的那首"的缓冲快照：已缓存的区间 + 远端声明的总长。
 *
 * 只保留当前播放项的账目，其它歌的下载不参与进度显示——进度条要回答的是
 * "我现在这首歌还有多久能听"。
 *
 * 存区间而不是单一比例：跳转之后缓存会变成几段（跳过的部分还没下、跳转之后的那段已经下好），
 * 单一比例会把"能一口气播到哪里"画错。
 */
internal data class WebDavBufferSnapshot(
    val key: String,
    val covered: List<CachedRange>,
    val total: Long,
) {
    /** 换算成整首的比例区间；远端没报总长度时为空列表（UI 什么都不画）。 */
    val ranges: List<BufferedRange>
        get() {
            if (total <= 0L) return emptyList()
            val scale = total.toFloat()
            return covered.map { range ->
                BufferedRange(
                    startFraction = (range.start / scale).coerceIn(0f, 1f),
                    endFraction = ((range.endInclusive + 1L) / scale).coerceIn(0f, 1f),
                )
            }
        }
}
