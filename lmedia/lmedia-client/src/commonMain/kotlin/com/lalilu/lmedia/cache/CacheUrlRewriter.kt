package com.lalilu.lmedia.cache

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaUrlRewriter
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import org.koin.core.annotation.Single

/**
 * 把在线播放地址换成本机回环地址的唯一实现。
 *
 * 数据源一行缓存代码都不用写：只要它在 [PlatformMediaSource] 里登记过，播放时就会经过这里，
 * 由它向控制器注册"这个键对应哪个远端地址、要带什么头、文件多大"，再返回回环地址。
 *
 * 不改写的三种情况：数据源已不在快照里、平台没有文件系统、地址不是 http(s)。
 */
@Single(binds = [MediaUrlRewriter::class])
internal class CacheUrlRewriter(
    /**
     * 延迟注入：数据源清单由域模块以 `Scope` 方式提供，构造期取它会让 Koin 图校验多出一条
     * "本模块依赖域模块"的硬边；改成用时再取，既不影响播放，也让模块可独立校验。
     */
    private val sources: Lazy<PlatformMediaSource>,
    private val controller: MediaCacheController,
) : MediaUrlRewriter {

    override suspend fun rewrite(audio: LAudio, originUrl: String): String {
        val source = sources.value.findSource(audio.mediaSourceName) ?: return originUrl
        val dataSource = source.dataSource
        // 没有专门的键就用地址本身：公开音源这样就已足够，带签名的源必须自己覆盖
        val rawKey = dataSource.cacheKey(audio) ?: originUrl
        val origin = CachedOrigin(
            url = originUrl,
            headers = dataSource.originHeaders(audio),
            totalSize = dataSource.cacheTotalSize(audio) ?: 0L,
            contentType = contentTypeOf(originUrl),
        )
        return controller.register(rawKey, origin) ?: originUrl
    }
}
