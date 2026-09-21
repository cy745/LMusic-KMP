package com.lalilu.lmedia.source.webdav

/**
 * "正在播的那首"的缓冲进度快照：已缓存字节 / 远端声明的总长。
 *
 * 只保留当前播放项的账目，其它歌的下载不参与进度显示——进度条要回答的是
 * "我现在这首歌还有多久能听"。
 */
internal data class WebDavBufferSnapshot(
    val key: String,
    val filled: Long,
    val total: Long,
) {
    /** 0f..1f；远端没报总长度时为 null（UI 按"未知"处理）。 */
    val fraction: Float?
        get() = total.takeIf { it > 0L }?.let { (filled.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
}
