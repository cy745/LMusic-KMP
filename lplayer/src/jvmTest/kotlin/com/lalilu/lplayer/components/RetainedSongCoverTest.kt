package com.lalilu.lplayer.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RetainedSongCoverTest {
    @Test fun pendingReplacementKeepsOldImageAndNewImageFadesOverIt() = runComposeUiTest {
        val loaded = mutableStateOf<Painter?>(ColorPainter(Color.Red))
        setContent { RetainedSongCoverImage(loaded.value, Modifier.size(64.dp).testTag("cover")) }
        waitForIdle()
        mainClock.autoAdvance = false
        fun pixel() = onNodeWithTag("cover").captureToImage().toPixelMap()[20, 20]
        runOnIdle { loaded.value = null }
        mainClock.advanceTimeBy(250)
        waitForIdle()
        assertTrue(pixel().red > 0.99f, "Loading or failure must retain the previous image")
        runOnIdle { loaded.value = ColorPainter(Color.Blue) }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        mainClock.advanceTimeBy(80)
        waitForIdle()
        val mixed = pixel()
        assertTrue(mixed.red > 0.05f && mixed.blue > 0.05f, "Both images should be visible during transition: $mixed")
        assertTrue(mixed.alpha > 0.99f, "Old image must remain opaque below the entering image")
        mainClock.advanceTimeBy(250)
        waitForIdle()
        assertTrue(pixel().blue > 0.99f)
    }
}
