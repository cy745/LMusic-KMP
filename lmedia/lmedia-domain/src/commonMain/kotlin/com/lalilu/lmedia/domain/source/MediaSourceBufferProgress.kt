package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.flow.Flow

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
     * 指定歌曲的已缓冲比例（0f..1f）。
     *
     * 返回 null 表示无法判断（例如远端没报总长度）。流在"这首歌开始播放/开始缓冲"之前
     * 可以不发射任何值；UI 按 0 显示。
     */
    fun bufferProgress(audioId: String): Flow<Float?>
}
