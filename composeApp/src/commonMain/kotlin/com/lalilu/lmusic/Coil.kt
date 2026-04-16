package com.lalilu.lmusic

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled
import com.lalilu.lmedia.coil.LAudioFetcherFactory
import com.lalilu.lmedia.coil.LAudioKeyer
import com.lalilu.lmusic.util.CoilLogger

fun platformSetupCoil(
    components: ComponentRegistry.Builder.() -> Unit = {},
    block: ImageLoader.Builder.() -> ImageLoader.Builder = { this }
) {
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(it)
            .serviceLoaderEnabled(true)
            .logger(CoilLogger)
            .components {
                add(LAudioFetcherFactory())
                add(LAudioKeyer())
                components()
            }
            .block()
            .build()
    }
}