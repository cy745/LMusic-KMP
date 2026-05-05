package com.lalilu.lmusic

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled
import com.lalilu.lmedia.coil.LAlbumKeyer
import com.lalilu.lmedia.coil.LAlbumMapper
import com.lalilu.lmedia.coil.LAudioFetcherFactory
import com.lalilu.lmedia.coil.LAudioKeyer

fun platformSetupCoil(
    components: ComponentRegistry.Builder.() -> Unit = {},
    block: ImageLoader.Builder.() -> ImageLoader.Builder = { this }
) {
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(it)
            .serviceLoaderEnabled(true)
//            .logger(KermitCoilLogger(Logger.withTag("Coil")))
            .components {
                add(LAudioFetcherFactory())
                add(LAudioKeyer())
                add(LAlbumKeyer())
                add(LAlbumMapper())
                components()
            }
            .block()
            .build()
    }
}