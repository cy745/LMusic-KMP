package com.lalilu.lplayer.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
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
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.ktx.animateColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MaterialKolorRecompositionTest {
    @Test
    fun animatedThemeInvalidatesEvenANonReadingDescendant() {
        val animated = observeTheme(animate = true)
        val immediate = observeTheme(animate = false)
        println("KOLOR animated=$animated immediate=$immediate")
        assertEquals(1, animated.rootCommits, "The caller above the theme updates once for a seed change")
        assertEquals(1, immediate.rootCommits)
        assertTrue(animated.readerCommits > immediate.readerCommits)
        assertTrue(animated.nonReaderCommits > immediate.nonReaderCommits,
            "Static theme invalidation also reaches descendants that do not read a theme color")
        assertEquals(1, immediate.readerCommits)
        assertEquals(1, immediate.nonReaderCommits)
        assertTrue(animated.distinctPrimaryColors > 2, "An actual interpolation must be observed")
        assertEquals(2, immediate.distinctPrimaryColors)
        assertEquals(0, animated.commitsAfterSettling, "The theme must stop composing when the animation ends")
        assertEquals(0, immediate.commitsAfterSettling)
    }

    @Test
    fun writingTheSameSeedDoesNotStartAnAnimation() {
        val observation = observeTheme(animate = true, nextSeed = Color.Red)
        println("KOLOR unchanged=$observation")
        assertEquals(0, observation.rootCommits)
        assertEquals(0, observation.readerCommits)
        assertEquals(0, observation.nonReaderCommits)
    }

    @Test
    fun executingTheAnimatedSchemeHelperAfterSettlingStillPublishesANewIdentity() = runComposeUiTest {
        val refresh = mutableStateOf(0)
        val target = lightColorScheme()
        val counter = Counter()
        val primaries = mutableListOf<Color>()
        mainClock.autoAdvance = false
        setContent { ForcedSchemeHost(refresh, target, counter, primaries) }
        repeat(3) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val before = runOnIdle { counter.count }
        repeat(3) { index ->
            runOnUiThread { refresh.value = index + 1 }
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
        assertEquals(before + 3, runOnIdle { counter.count })
        assertEquals(1, runOnIdle { primaries.distinct().size })
    }

    @Test
    fun identicalColorSchemeCopiesStillInvalidateTheStaticTheme() = runComposeUiTest {
        val first = lightColorScheme()
        val second = first.copy()
        assertNotEquals(first, second, "ColorScheme equality uses identity in the installed Material3")
        assertEquals(first.primary, second.primary)
        val scheme = mutableStateOf(first)
        val counter = Counter()
        setContent { MaterialTheme(colorScheme = scheme.value) { NonReadingHost(counter) } }
        val before = runOnIdle { counter.count }
        runOnUiThread { scheme.value = second }
        waitForIdle()
        assertEquals(before + 1, runOnIdle { counter.count })
    }

    @Test
    fun aColorAnimationReadDuringDrawingDoesNotComposeEveryFrame() = runComposeUiTest {
        val target = mutableStateOf(Color.Red)
        val counter = Counter()
        mainClock.autoAdvance = false
        setContent {
            val color = animateColorAsState(target.value, tween(160))
            DrawingHost(color, counter)
        }
        repeat(3) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val before = runOnIdle { counter.count }
        val firstPixel = onNodeWithTag("draw-animation").captureToImage().toPixelMap()[10, 10]
        runOnUiThread { target.value = Color.Blue }
        repeat(18) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val lastPixel = onNodeWithTag("draw-animation").captureToImage().toPixelMap()[10, 10]
        assertNotEquals(firstPixel, lastPixel)
        assertEquals(before, runOnIdle { counter.count })
    }

    private fun observeTheme(animate: Boolean, nextSeed: Color = Color.Blue): Observation {
        lateinit var result: Observation
        runComposeUiTest {
            val seed = mutableStateOf(Color.Red)
            val root = Counter()
            val reader = Counter()
            val nonReader = Counter()
            val colors = mutableListOf<Color>()
            mainClock.autoAdvance = false
            setContent {
                SideEffect { root.count++ }
                DynamicMaterialTheme(seedColor = seed.value, isDark = false,
                    animate = animate, animationSpec = tween(160)) {
                    ReadingHost(reader, colors)
                    NonReadingHost(nonReader)
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame(); waitForIdle() }
            val before = runOnIdle { Triple(root.count, reader.count, nonReader.count) }
            runOnUiThread { seed.value = nextSeed }
            repeat(18) { mainClock.advanceTimeByFrame(); waitForIdle() }
            val after = runOnIdle { Triple(root.count, reader.count, nonReader.count) }
            repeat(8) { mainClock.advanceTimeByFrame(); waitForIdle() }
            result = runOnIdle {
                Observation(after.first - before.first, after.second - before.second,
                    after.third - before.third, colors.distinct().size, reader.count - after.second)
            }
        }
        return result
    }
}

private data class Observation(val rootCommits: Int, val readerCommits: Int,
    val nonReaderCommits: Int, val distinctPrimaryColors: Int, val commitsAfterSettling: Int)

private class Counter { var count = 0 }

@Composable
private fun ReadingHost(counter: Counter, colors: MutableList<Color>) {
    val color = MaterialTheme.colorScheme.primary
    SideEffect { counter.count++; colors.add(color) }
    Box(Modifier.size(20.dp).drawBehind { drawRect(color) })
}

@Composable
private fun NonReadingHost(counter: Counter) {
    SideEffect { counter.count++ }
    Box(Modifier.size(20.dp).drawBehind { drawRect(Color.Black) })
}

@Composable
private fun DrawingHost(color: State<Color>, counter: Counter) {
    SideEffect { counter.count++ }
    Box(Modifier.testTag("draw-animation").size(20.dp).drawBehind { drawRect(color.value) })
}

@Composable
private fun ForcedSchemeHost(refresh: State<Int>, target: ColorScheme,
    counter: Counter, primaries: MutableList<Color>) {
    val refreshKey = refresh.value
    val scheme = animateColorScheme(target)
    SideEffect {
        check(refreshKey >= 0)
        primaries.add(scheme.primary)
    }
    MaterialTheme(colorScheme = scheme) { NonReadingHost(counter) }
}
