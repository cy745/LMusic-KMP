package com.lalilu.lhome.extensions

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import coil3.*
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.SharedCoverImage
import com.lalilu.extensions.SharedMap
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class, coil3.annotation.DelicateCoilApi::class)
class SharedCoverHandoffTest {
    private data class Cover(val version: Int)
    @Test fun firstDetailFrameUsesSourceImageWhileLargeImageIsStillLoading() = runComposeUiTest {
        val context = PlatformContext.INSTANCE
        val previous = SingletonImageLoader.get(context)
        val releaseLarge = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        fun picture(color: Int) = object : Image {
            override val width = 32
            override val height = 32
            override val size = 4096L
            override val shareable = true
            override fun draw(canvas: Canvas) { canvas.clear(color) }
        }
        val loader = ImageLoader.Builder(context).components {
            add(Fetcher.Factory<Cover> { data, _, _ ->
                Fetcher {
                    if (data.version == 1) releaseLarge.await()
                    if (data.version == 2) { refreshStarted.complete(Unit); releaseRefresh.await() }
                    ImageFetchResult(picture(when (data.version) {
                        0 -> org.jetbrains.skia.Color.RED
                        1 -> org.jetbrains.skia.Color.BLUE
                        else -> org.jetbrains.skia.Color.GREEN
                    }), false, DataSource.NETWORK)
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
        var navigation by mutableStateOf<SharedMap?>(null)
        var revision by mutableIntStateOf(0)
        try {
            setContent {
                SharedTransitionLayout {
                    val transition = this
                    AnimatedContent(navigation, label = "test-navigation") { target ->
                    SharedContext(
                        sharedMap = target ?: mapOf("COVER" to "test-cover"),
                        sharedTransitionScope = transition,
                        defaultAnimationScope = this,
                    ) {
                        val model = remember(target, revision) {
                            ImageRequest.Builder(context).data(Cover(if (target == null) 0 else revision + 1))
                                .size(if (target == null) 32 else 200)
                                .memoryCacheKey(MemoryCache.Key(if (target == null) "small" else "large-$revision",
                                    mapOf("variant" to "exact-extra")))
                                .build()
                        }
                        SharedCoverImage(model, null, Modifier.size(if (target == null) 80.dp else 200.dp)
                            .sharedElementV2("COVER").testTag("cover")
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navigation = sharedMap })
                    }
                    }
                }
            }
            waitUntil(timeoutMillis = 5_000) { loader.memoryCache!!.keys.any { it.key == "small" } }
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithTag("cover").performClick()
            repeat(5) { frame ->
                mainClock.advanceTimeByFrame()
                waitForIdle()
                val during = onRoot().captureToImage().toPixelMap()[20, 20]
                assertTrue(during.red > .9f && during.green < .1f,
                    "Shared overlay must keep source pixels in frame $frame: $during")
            }
            mainClock.autoAdvance = true
            waitForIdle()
            assertTrue(!releaseLarge.isCompleted)
            val color = onNodeWithTag("cover").captureToImage().toPixelMap()[80, 80]
            assertTrue(color.red > .9f && color.green < .1f, "Source cover must remain visible during cold detail load: $color")
            releaseLarge.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { loader.memoryCache!!.keys.any { it.key == "large-0" } }
            waitForIdle()
            runOnIdle { revision = 1 }
            waitUntil(timeoutMillis = 5_000) { refreshStarted.isCompleted }
            waitForIdle()
            val retained = onNodeWithTag("cover").captureToImage().toPixelMap()[80, 80]
            assertTrue(retained.blue > .9f && retained.red < .1f,
                "Refreshing metadata must retain the current large cover, not revert to the source thumbnail: $retained")
            releaseRefresh.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { loader.memoryCache!!.keys.any { it.key == "large-1" } }
            waitForIdle()
            assertTrue(onNodeWithTag("cover").captureToImage().toPixelMap()[80, 80].green > .9f)
        } finally {
            releaseLarge.complete(Unit)
            releaseRefresh.complete(Unit)
            SingletonImageLoader.setUnsafe(previous)
        }
    }
}
