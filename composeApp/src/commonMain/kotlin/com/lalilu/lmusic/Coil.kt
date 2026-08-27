package com.lalilu.lmusic

import co.touchlab.kermit.Logger
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled
import com.lalilu.lmedia.coil.*
import com.lalilu.lmusic.util.KermitCoilLogger

fun platformSetupCoil(
    components: ComponentRegistry.Builder.() -> Unit = {},
    block: ImageLoader.Builder.() -> ImageLoader.Builder = { this }
) {
    SingletonImageLoader.setSafe {
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
