package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio

/**
 * 播放地址的统一改写入口：把数据源给出的原始地址换成本机缓存代理地址。
 *
 * 由缓存模块实现并通过 Koin 注册。未注册时 [resolveMediaData] 原样返回源给出的地址，
 * 行为与没有缓存时完全一致 —— 这是"所有在线源无需任何处理就能获得缓存"的接入点。
 *
 * 实现方只应改写 `http(s)` 地址；本地文件、`content://`、字节数据必须原样返回。
 */
interface MediaUrlRewriter {
    /** 返回可播放地址；不需要改写时返回 [originUrl] 本身。 */
    fun rewrite(audio: LAudio, originUrl: String): String
}
