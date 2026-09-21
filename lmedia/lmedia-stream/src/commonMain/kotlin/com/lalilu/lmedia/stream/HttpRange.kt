package com.lalilu.lmedia.stream

/**
 * 单区间 `Range` 请求（闭区间）。
 *
 * 只支持单区间：ExoPlayer / AVPlayer / VLC 都只发单区间，多区间属于罕见场景，
 * 遇到时退回整文件响应比猜一个合并区间更安全。
 */
data class ByteRange(
    val start: Long,
    val endInclusive: Long,
) {
    val length: Long get() = endInclusive - start + 1
}

enum class RangeDisposition {
    /** 没有 `Range` 头或无法理解：按整文件响应。 */
    WHOLE_FILE,

    /** 解析出可服务的区间。 */
    SATISFIABLE,

    /** 起点超过文件末尾：按 RFC 7233 回 416。 */
    UNSATISFIABLE,
}

data class RangeResolution(
    val disposition: RangeDisposition,
    val range: ByteRange? = null,
)

/** 本机回环代理的 `Range` 头解析；与具体媒体来源无关。 */
object HttpRange {
    private const val PREFIX = "bytes="

    /**
     * 解析 `Range` 头。[totalSize] 小于等于 0 表示长度未知，此时只能整文件响应。
     */
    fun resolve(header: String?, totalSize: Long): RangeResolution {
        val raw = header?.trim().orEmpty()
        if (raw.isEmpty()) return RangeResolution(RangeDisposition.WHOLE_FILE)
        if (!raw.startsWith(PREFIX, ignoreCase = true)) {
            return RangeResolution(RangeDisposition.WHOLE_FILE)
        }
        if (totalSize <= 0L) return RangeResolution(RangeDisposition.WHOLE_FILE)

        val spec = raw.substring(PREFIX.length).trim()
        // 多区间（`bytes=0-1,5-6`）不在支持范围，退回整文件
        if (spec.contains(',')) return RangeResolution(RangeDisposition.WHOLE_FILE)

        val dash = spec.indexOf('-')
        if (dash < 0) return RangeResolution(RangeDisposition.WHOLE_FILE)
        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()
        if (startText.isEmpty() && endText.isEmpty()) {
            return RangeResolution(RangeDisposition.WHOLE_FILE)
        }

        // 后缀区间 `bytes=-N`：最后 N 个字节
        if (startText.isEmpty()) {
            val suffix = endText.toLongOrNull() ?: return RangeResolution(RangeDisposition.WHOLE_FILE)
            if (suffix <= 0L) return RangeResolution(RangeDisposition.WHOLE_FILE)
            val start = (totalSize - suffix).coerceAtLeast(0L)
            return RangeResolution(RangeDisposition.SATISFIABLE, ByteRange(start, totalSize - 1))
        }

        val start = startText.toLongOrNull() ?: return RangeResolution(RangeDisposition.WHOLE_FILE)
        if (start < 0L) return RangeResolution(RangeDisposition.WHOLE_FILE)
        if (start >= totalSize) return RangeResolution(RangeDisposition.UNSATISFIABLE)

        val requestedEnd = when {
            endText.isEmpty() -> totalSize - 1
            else -> endText.toLongOrNull() ?: return RangeResolution(RangeDisposition.WHOLE_FILE)
        }
        if (requestedEnd < start) return RangeResolution(RangeDisposition.WHOLE_FILE)

        val endInclusive = minOf(requestedEnd, totalSize - 1)
        return RangeResolution(RangeDisposition.SATISFIABLE, ByteRange(start, endInclusive))
    }

    /** 从 `Content-Range: bytes 0-1/32239619` 取出总长度。 */
    fun totalSizeFromContentRange(value: String?): Long? {
        val total = value?.substringAfterLast('/', "")?.trim()?.toLongOrNull() ?: return null
        return total.takeIf { it > 0L }
    }
}
