package com.lalilu.lmusic

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.serviceLoaderEnabled
import coil3.util.DebugLogger
import com.lalilu.lmedia.coil.LAudioMapper
import com.lalilu.lmedia.coil.SourceItemFetcherFactory

@Composable
fun platformSetupCoil(block: ImageLoader.Builder.() -> ImageLoader.Builder = { this }) {
    setSingletonImageLoaderFactory {
        ImageLoader.Builder(it)
            .serviceLoaderEnabled(true)
            .logger(DebugLogger())
            .components {
                add(SourceItemFetcherFactory())
                add(LAudioMapper())
            }
            .block()
            .build()
    }
}