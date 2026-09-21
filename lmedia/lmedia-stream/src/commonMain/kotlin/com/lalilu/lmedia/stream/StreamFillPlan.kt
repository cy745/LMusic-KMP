package com.lalilu.lmedia.stream

/**
 * 决定下一段该补哪里。返回 null 表示"现在没有该补的"。
 *
 * 三档优先级，越靠近播放头越急：
 *
 * 1. **播放头及其前瞻窗口**——播放卡住就是这里没到。这一档永远做，因为它补的正是播放器接下来
 *    一定会要的字节。
 * 2. **从播放头往后顺序铺满**——"开播后主动缓存完整歌曲"：用户还没听到，但迟早会听到。
 * 3. **播放器跳过留下的空洞**——最低。用户跳过去的部分大概率不重要，随时可以被前两档抢走。
 *
 * [allowBackground] 为 false（例如计费网络）时只做第 1 档：不拿用户的流量预取。
 * [lookaheadBytes] 为 0 表示没有前瞻窗口（第 1 档不做，等于只看播放器自己的请求）。
 *
 * 每一档里都取"最靠前的那一段"——同一档内前面对后面有依赖（听到 A 才会听到 B），而跨档的偏好
 * 由上面三档决定。
 */
internal fun nextFillRange(
    coverage: List<CachedRange>,
    totalSize: Long,
    playhead: Long,
    lookaheadBytes: Long,
    allowBackground: Boolean,
): CachedRange? {
    if (totalSize <= 0L) return null
    val last = totalSize - 1L
    val position = playhead.coerceIn(0L, last)

    // 1. 播放头与前瞻：这一档补的就是"马上要播到"的字节
    val lookaheadEnd = if (lookaheadBytes <= 0L) {
        position - 1L
    } else {
        minOf(last, position + lookaheadBytes - 1L)
    }
    if (lookaheadEnd >= position) {
        missingCoverage(coverage, position, lookaheadEnd).firstOrNull()?.let { return it }
    }

    // 计费网络下到此为止：后面的都是"用户还没要"的字节
    if (!allowBackground) return null

    // 2. 从前瞻窗口之后继续往后铺满
    if (lookaheadEnd < last) {
        missingCoverage(coverage, maxOf(position, lookaheadEnd + 1L), last).firstOrNull()?.let { return it }
    }

    // 3. 被跳过的空洞（播放头之前）
    return missingCoverage(coverage, 0L, last).firstOrNull()
}
