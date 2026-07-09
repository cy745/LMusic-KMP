package com.lalilu.lmusic

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled
import com.lalilu.lmedia.coil.LAlbumCoverFetcherFactory
import com.lalilu.lmedia.coil.LAlbumCoverKeyer
import com.lalilu.lmedia.coil.LArtistCoverFetcherFactory
import com.lalilu.lmedia.coil.LArtistCoverKeyer
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