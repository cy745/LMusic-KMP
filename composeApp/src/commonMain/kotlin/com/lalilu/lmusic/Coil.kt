package com.lalilu.lmusic

import co.touchlab.kermit.Logger
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled
import com.lalilu.lmedia.coil.*
import com.lalilu.lmusic.util.KermitCoilLogger

@OptIn(coil3.annotation.DelicateCoilApi::class)
fun platformSetupCoil(
    components: ComponentRegistry.Builder.() -> Unit = {},
    block: ImageLoader.Builder.() -> ImageLoader.Builder = { this }
) {
    // setUnsafe 强制覆盖：setSafe 在 SingletonImageLoader 已被 factory/默认实例
    // 占用时会静默忽略，自定义 fetcher（MediaCoverRequest/LAudio）不生效，
    // 出现 "Unable to create a fetcher that supports"。
    coil3.SingletonImageLoader.setUnsafe {
        ImageLoader.Builder(it)
            .serviceLoaderEnabled(true)
            .logger(KermitCoilLogger(Logger.withTag("Coil")))
            .components {
                add(LAudioFetcherFactory())
                add(LAudioKeyer())
                add(MediaCoverRequestFetcherFactory())
                add(MediaCoverRequestKeyer())
                add(LAlbumCoverFetcherFactory())
                add(LAlbumCoverKeyer())
                add(LArtistCoverFetcherFactory())
                add(LArtistCoverKeyer())
                components()
            }
            .block()
            .build()
    }
}
