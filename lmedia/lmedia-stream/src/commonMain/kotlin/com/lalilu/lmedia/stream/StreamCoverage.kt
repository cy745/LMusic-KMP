package com.lalilu.lmedia.stream

/**
 * 一段已缓存的字节区间（闭区间）。
 *
 * 与具体来源无关：任何"能按区间取字节"的来源都复用同一套覆盖模型。
 */
data class CachedRange(val start: Long, val endInclusive: Long) {
    init {
        require(start >= 0L) { "Range start must not be negative: $start" }
        require(endInclusive >= start) { "Range end must not precede start: $start..$endInclusive" }
    }

    val length: Long get() = endInclusive - start + 1

    fun contains(position: Long): Boolean = position in start..endInclusive
}

/**
 * 合并任意顺序、可能重叠或相邻的区间，得到有序且互不相邻的覆盖列表。
 *
 * 相邻也算相连（`[0,9]` 与 `[10,19]` 合并成 `[0,19]`）：读起来本来就连续，分开只会让
 * "这段有没有缓存"的判断多绕一圈。
 */
internal fun normalizeCoverage(ranges: List<CachedRange>): List<CachedRange> {
    if (ranges.isEmpty()) return emptyList()
    val sorted = ranges.sortedBy { it.start }
    val merged = mutableListOf<CachedRange>()
    var current = sorted.first()
    for (next in sorted.drop(1)) {
        current = if (next.start <= current.endInclusive + 1) {
            CachedRange(current.start, maxOf(current.endInclusive, next.endInclusive))
        } else {
            merged += current
            next
        }
    }
    merged += current
    return merged
}

/**
 * 覆盖列表里 `[from, toInclusive]` 尚未缓存的空洞；已全覆盖时返回空列表。
 *
 * 入参必须是 [normalizeCoverage] 过的列表（有序、互不相邻）。
 */
internal fun missingCoverage(
    covered: List<CachedRange>,
    from: Long,
    toInclusive: Long,
): List<CachedRange> {
    if (toInclusive < from) return emptyList()
    val holes = mutableListOf<CachedRange>()
    var position = from

    for (range in covered) {
        if (range.endInclusive < position) continue
        if (range.start > toInclusive) break

        if (range.start > position) {
            holes += CachedRange(position, range.start - 1)
        }
        position = maxOf(position, range.endInclusive + 1)
        if (position > toInclusive) return holes
    }

    if (position <= toInclusive) holes += CachedRange(position, toInclusive)
    return holes
}

/**
 * 从 [position] 起连续可读的一段（长度上限 [maxLength]）；该位置没有缓存时返回 null。
 *
 * 返回值可能短于 [maxLength]——调用方需要按返回长度推进，不能假设一次读满。
 */
internal fun contiguousCoverage(
    covered: List<CachedRange>,
    position: Long,
    maxLength: Long,
): CachedRange? {
    if (maxLength <= 0L) return null
    val range = covered.firstOrNull { it.contains(position) } ?: return null
    return CachedRange(position, minOf(range.endInclusive, position + maxLength - 1))
}

/** `[from, toInclusive]` 是否已全部缓存。 */
internal fun isCoverageComplete(
    covered: List<CachedRange>,
    from: Long,
    toInclusive: Long,
): Boolean = toInclusive < from || covered.any { it.start <= from && it.endInclusive >= toInclusive }

/** 覆盖的总字节数。 */
internal fun coverageBytes(covered: List<CachedRange>): Long = covered.sumOf { it.length }
