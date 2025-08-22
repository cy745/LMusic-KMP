package com.lalilu.lmusic

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.serviceLoaderEnabled
import com.lalilu.lmedia.coil.LAudioFetcherFactory
import com.lalilu.lmedia.coil.LAudioKeyer
import com.lalilu.lmusic.util.KermitCoilLogger

@Composable
fun platformSetupCoil(
    components: ComponentRegistry.Builder.() -> Unit = {},
    block: ImageLoader.Builder.() -> ImageLoader.Builder = { this }
) {
    setSingletonImageLoaderFactory {
        ImageLoader.Builder(it)
            .serviceLoaderEnabled(true)
            .logger(KermitCoilLogger(Logger.withTag("Coil")))
            .components {
                add(LAudioFetcherFactory())
                add(LAudioKeyer())
                components()
            }
            .block()
            .build()
    }
}