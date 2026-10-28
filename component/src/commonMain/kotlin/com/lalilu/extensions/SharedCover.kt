package com.lalilu.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade

private const val CoverKey = "_coverMemoryKey"
private const val CoverExtra = "_coverMemoryExtra:"

/** Navigation carries only cache-key metadata, never a bitmap or a second image cache. */
internal fun SharedMap.withCoverKey(key: MemoryCache.Key?): SharedMap =
    filterKeys { it != CoverKey && !it.startsWith(CoverExtra) } +
        if (key == null) emptyMap() else buildMap {
            put(CoverKey, key.key)
            key.extras.forEach { (name, value) -> put(CoverExtra + name, value) }
        }

internal fun SharedMap.coverKey(): MemoryCache.Key? = get(CoverKey)?.let { key ->
    MemoryCache.Key(key, filterKeys { it.startsWith(CoverExtra) }.mapKeys { it.key.removePrefix(CoverExtra) })
}

class SharedCoverHandoff internal constructor(val incoming: MemoryCache.Key?) {
    // Read by click callbacks on the UI thread; changing this does not recompose/restart an image.
    var displayed: MemoryCache.Key? = incoming
}

val LocalSharedCoverHandoff = staticCompositionLocalOf<SharedCoverHandoff?> { null }

@Composable
fun rememberSharedCoverRequest(model: Any?): ImageRequest {
    val context = LocalPlatformContext.current
    val incoming = LocalSharedCoverHandoff.current?.incoming
    return remember(context, model, incoming) {
        (if (model is ImageRequest) model.newBuilder() else ImageRequest.Builder(context).data(model))
            .apply { if (incoming != null) placeholderMemoryCacheKey(incoming) }
            .crossfade(false)
            .build()
    }
}

/** Keep the source's displayed image as the destination placeholder while loading the larger image. */
@Composable
fun SharedCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val handoff = LocalSharedCoverHandoff.current
    var ready by remember(handoff) { mutableStateOf<Painter?>(null) }
    AsyncImage(
        model = rememberSharedCoverRequest(model),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        transform = { state ->
            when (state) {
                is AsyncImagePainter.State.Loading -> state.copy(painter = ready ?: state.painter)
                is AsyncImagePainter.State.Error -> state.copy(painter = ready ?: state.painter)
                else -> state
            }
        },
        onState = { state ->
            if (state is AsyncImagePainter.State.Success) {
                ready = state.painter
                handoff?.displayed = state.result.memoryCacheKey
            }
        },
    )
}
