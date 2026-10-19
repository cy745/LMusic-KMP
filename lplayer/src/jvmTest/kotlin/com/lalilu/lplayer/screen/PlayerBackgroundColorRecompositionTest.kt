package com.lalilu.lplayer.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import com.lalilu.LMusicTheme
import com.lalilu.LocalSeedColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class PlayerBackgroundColorRecompositionTest {
    @Test
    fun appThemeSeedChangeCommitsOnceWhileThePlayerBackgroundKeepsAnimating() = runComposeUiTest {
        lateinit var seed: MutableState<Color>
        val counter = CommitCounter()
        mainClock.autoAdvance = false
        setContent {
            LMusicTheme(isDarkTheme = false, globalFontFamily = FontFamily.Default) {
                seed = LocalSeedColor.current
                BackgroundHost(counter)
            }
        }
        runOnUiThread { seed.value = Color.Red }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        val before = runOnIdle { counter.count }
        val first = onNodeWithTag("background").captureToImage().toPixelMap()[20, 20]
        runOnUiThread { seed.value = Color.Blue }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        mainClock.advanceTimeBy(160)
        waitForIdle()
        val intermediate = onNodeWithTag("background").captureToImage().toPixelMap()[20, 20]
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        val last = onNodeWithTag("background").captureToImage().toPixelMap()[20, 20]
        assertNotEquals(first, intermediate, "The local background must start moving toward the new theme")
        assertNotEquals(intermediate, last, "The background must retain a local transition")
        assertEquals(before + 1, runOnIdle { counter.count },
            "One final theme update is allowed; local animation frames must not compose the host")
    }

    @Test
    fun replacingTheMaterialColorSchemeInvalidatesThePlayerBackgroundHost() = runComposeUiTest {
        val scheme = mutableStateOf(lightColorScheme(primaryContainer = Color.Red))
        val onCommit = CommitCounter()
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme(colorScheme = scheme.value) {
                BackgroundHost(onCommit)
            }
        }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        val before = runOnIdle { onCommit.count }
        val firstPixel = onNodeWithTag("background").captureToImage().toPixelMap()[20, 20]
        // Reproduce the old global animation's succession of ColorSchemes.
        repeat(8) { frame ->
            runOnUiThread {
                scheme.value = lightColorScheme(primaryContainer = Color(1f - (frame + 1) / 8f, 0f, (frame + 1) / 8f))
            }
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        val lastPixel = onNodeWithTag("background").captureToImage().toPixelMap()[20, 20]
        assertNotEquals(firstPixel, lastPixel, "The visual background must follow the new theme")
        // Diagnostic reproducer: a State-returning helper does not isolate theme invalidation.
        assertEquals(before + 8, runOnIdle { onCommit.count }, "Each ColorScheme replacement invalidates the host")
    }
}

private class CommitCounter {
    var count = 0
}

@Composable
private fun BackgroundHost(counter: CommitCounter) {
    // 与 PlayerScreen 当前实现保持一致：背景色在页面组合作用域内做局部弹簧过渡，
    // 动画 State 只在绘制期读取，因此逐帧动画不会让宿主重组。
    val background = animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primaryContainer,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "PlayerBackgroundColor",
    )
    SideEffect { counter.count++ }
    Box(Modifier.testTag("background").size(40.dp).drawBehind { drawRect(background.value) })
}
