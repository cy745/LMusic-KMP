package com.lalilu.lplayer.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import com.lalilu.extensions.Item
import com.lalilu.extensions.rotationalDiff
import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.*
import kotlin.test.assertTrue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class QueueSwipeUiTest {
    @Test fun pendingIntentAddedDuringRebaseDoesNotBlockQueueUpdates() = runComposeUiTest {
        val songs = (0..29).map { LAudio(id = "$it", mediaSourceName = "local") }
        val original = songs.map { Item(it, "song:${it.id}") }
        var rows by mutableStateOf(original)
        var pending by mutableStateOf(false)
        var completed = false
        lateinit var select: () -> Unit
        setContent {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = 10,
                initialFirstVisibleItemScrollOffset = 16)
            val scope = rememberCoroutineScope()
            select = {
                scope.launch {
                    val updated = original.rotationalDiff(songs.drop(12) + songs.take(12), { it.id })
                    updatePlaylistAtTop(original, updated, state,
                        leadingItemCount = { if (pending) 1 else 0 }) {
                        rows = it
                        pending = true // Independent pending-next flow changes while rebasing.
                    }
                    completed = true
                }
            }
            LazyColumn(Modifier.width(360.dp).height(400.dp), state = state) {
                if (pending) item(key = "pending") { Box(Modifier.height(80.dp)) }
                items(rows, key = { it.key }) { Box(Modifier.height(80.dp)) }
            }
        }
        waitForIdle()
        runOnIdle { select() }
        waitForIdle()
        runOnIdle {
            assertTrue(completed, "A pending-next row must not suspend queue observation")
            assertEquals(30, rows.size)
            assertEquals("12", rows.first().data.id)
        }
    }

    @Test fun pickingVisibleSongWhileScrolledRetainsCoverCompositions() = verifyNativeSelection(0)
    @Test fun pickingSongWithPartiallyClippedFirstRowRetainsNativeAnimation() = verifyNativeSelection(16)

    @Test fun pickingLastSongKeepsItsCoverAndFadesPrecedingRows() = verifyNativeSelection(0, 14, 15)

    private fun verifyNativeSelection(initialOffset: Int, selection: Int = 12, count: Int = 30) = runComposeUiTest {
        val songs = (0 until count).map { LAudio(id = "$it", mediaSourceName = "local") }
        var rows by mutableStateOf(songs.map { Item(it, "song:${it.id}") })
        val disposed = mutableListOf<String>()
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        setContent {
            MaterialTheme {
                listState = rememberLazyListState(initialFirstVisibleItemIndex = 10, initialFirstVisibleItemScrollOffset = initialOffset)
                val scope = rememberCoroutineScope()
                Box(Modifier.width(360.dp).height(400.dp).background(Color.White).testTag("native-list")) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(rows, key = { it.key }) { row ->
                            DisposableEffect(row.key) { onDispose { disposed += row.key } }
                            val cover = remember { androidx.compose.ui.graphics.painter.ColorPainter(
                                if (row.data.id.toInt() < selection) Color.Blue else Color.Red) }
                            androidx.compose.foundation.layout.Row(
                                Modifier.animateItem().fillMaxWidth().height(80.dp)
                                    .testTag("select-${row.data.id}")
                                    .then(Modifier.clickable {
                                        val pivot = songs.indexOf(row.data)
                                        val updated = rows.rotationalDiff(songs.drop(pivot) + songs.take(pivot), { it.id }, preferForwardRotation = true)
                                        val plan = planPlaylistScroll(rows, updated.first(),
                                            listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet(), true)
                                        scope.launch {
                                            if (plan == PlaylistScrollPlan.JumpToTop) {
                                                updatePlaylistAtTop(rows, updated, listState) { rows = it }
                                            } else rows = updated
                                        }
                                    })
                            ) {
                                RetainedSongCoverImage(cover, Modifier.size(64.dp))
                                Text(row.data.id)
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithTag("select-$selection").performTouchInput { click() }
        repeat(5) {
            mainClock.advanceTimeByFrame()
            waitForIdle() // Flush each draw as well as advancing the animation clock.
        }
        val movingTop = onNodeWithTag("select-$selection").fetchSemanticsNode().boundsInRoot.top
        assertTrue(movingTop > 0f && movingTop < (selection - 10) * 80f - initialOffset,
            "Retained row must be moving upwards through an intermediate position: $movingTop")
        val frame = onNodeWithTag("native-list").captureToImage()
        val pixels = frame.toPixelMap()
        val fading = pixels[20, 20]
        assertTrue(fading.blue > fading.red && fading.red > 0.01f,
            "Departing blue cover must be partially transparent over white, not instantly absent: $fading")
        mainClock.advanceTimeBy(1500)
        waitForIdle()
        mainClock.autoAdvance = true
        assertEquals("$selection", rows.first().data.id)
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
        for (id in selection..minOf(selection + 2, count - 1)) assertTrue("song:$id" !in disposed,
            "Retained row $id and its loaded cover were disposed during selection")
        assertEquals("song:$selection", rows.first().key)
        assertTrue(rows.first { it.data.id == "0" }.key != "song:0",
            "Rows before the selection must still leave and reappear at the end")
    }

    @Test fun schedulingNextBelowViewportKeepsVisibleAnchor() = runComposeUiTest {
        val songs = (0..29).map { LAudio(id = "$it", mediaSourceName = "local") }
        var rows by mutableStateOf(songs.map { Item(it, "song:${it.id}") })
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        setContent {
            MaterialTheme {
                listState = rememberLazyListState(initialFirstVisibleItemIndex = 10,
                    initialFirstVisibleItemScrollOffset = 16)
                val scope = rememberCoroutineScope()
                LazyColumn(Modifier.width(360.dp).height(400.dp), state = listState) {
                    items(rows, key = { it.key }) { row ->
                        SwipeableQueueItem(Modifier.fillMaxWidth().testTag("browse-${row.data.id}"), false,
                            onPlayNext = {
                                val old = rows
                                val updated = old.rotationalDiff(
                                    listOf(songs.first(), row.data) + songs.drop(1).filter { it.id != row.data.id },
                                    { it.id },
                                )
                                val plan = planPlaylistScroll(old, updated.first(),
                                    listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet(), pickedByUser = true)
                                // Exercise the same list update and scroll branches as PlaylistLayout.
                                scope.launch {
                                    when (plan) {
                                        PlaylistScrollPlan.KeepScroll -> rows = updated
                                        PlaylistScrollPlan.JumpToTop -> { rows = updated; listState.scrollToItem(0) }
                                    }
                                }
                            }, onRemove = {}) {
                            Box(Modifier.fillMaxWidth().height(80.dp)) { Text("歌曲 ${row.data.id}") }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val before = listState.layoutInfo.visibleItemsInfo.first().let { it.key to it.offset }
        onNodeWithTag("browse-12").performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals("12", rows[1].data.id)
        assertEquals(before, listState.layoutInfo.visibleItemsInfo.first().let { it.key to it.offset })
        assertTrue(listState.firstVisibleItemIndex > 0)
    }

    @Test fun horizontalDirectionsFireOnceAndItemCanBeSwipedAgain() = runComposeUiTest {
        var next = 0; var removed = 0; var clicks = 0; var longClicks = 0
        setContent {
            MaterialTheme {
                SwipeableQueueItem(Modifier.width(360.dp).background(MaterialTheme.colorScheme.background).testTag("row"), false,
                    { next++ }, { removed++ }) {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        SongCard(id = "preview", title = "Winter Sparkler", subtitle = "明透",
                            onClick = { clicks++ }, onLongClick = { longClicks++ })
                    }
                }
            }
        }
        onNodeWithTag("row").performTouchInput { down(centerRight); moveTo(centerLeft, 300) }
        waitForIdle()
        saveSwipe("queue-swipe-next.png")
        onNodeWithTag("row").performTouchInput { up() }
        waitForIdle()
        assertEquals(1, next); assertEquals(0, removed)
        onNodeWithTag("row").performTouchInput { down(centerLeft); moveTo(centerRight, 300) }
        waitForIdle()
        saveSwipe("queue-swipe-remove.png")
        onNodeWithTag("row").performTouchInput { up() }
        waitForIdle()
        assertEquals(1, next); assertEquals(1, removed)
        assertEquals(0, clicks); assertEquals(0, longClicks)
        onNodeWithTag("row").performTouchInput { click(); longClick() }
        waitForIdle()
        assertEquals(1, clicks); assertEquals(1, longClicks)
    }

    @Test fun shortVerticalAndCancelledGesturesDoNotMutateQueue() = runComposeUiTest {
        var actions = 0
        setContent {
            MaterialTheme {
                SwipeableQueueItem(Modifier.width(360.dp).background(MaterialTheme.colorScheme.background).testTag("row"), true,
                    { actions++ }, { actions++ }) { Box(Modifier.fillMaxWidth().height(96.dp)) }
            }
        }
        onNodeWithTag("row").performTouchInput { swipe(center, center + Offset(-30f, 0f)) }
        waitForIdle()
        onNodeWithTag("row").performTouchInput { swipeUp() }
        waitForIdle()
        onNodeWithTag("row").performTouchInput { down(centerRight); moveTo(centerLeft); cancel() }
        waitForIdle()
        assertEquals(0, actions)
    }
    @Test fun horizontalLockConsumesVerticalDriftButVerticalStartRemainsNestedScroll() = runComposeUiTest {
        var next = 0
        var nestedY = 0f
        val parent = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                nestedY += kotlin.math.abs(available.y)
                return Offset(0f, available.y)
            }
        }
        setContent {
            MaterialTheme {
                LazyColumn(Modifier.width(360.dp).height(400.dp).nestedScroll(parent)) {
                    items(20) { index ->
                        SwipeableQueueItem(Modifier.fillMaxWidth().testTag("nested-$index"), false,
                            { next++ }, {}) {
                            Box(Modifier.fillMaxWidth().height(96.dp)) { Text("歌曲 $index") }
                        }
                    }
                }
            }
        }
        onNodeWithTag("nested-0").performTouchInput {
            down(Offset(280f, 40f))
            moveTo(Offset(200f, 40f), 100) // Acquire horizontal ownership.
            moveTo(Offset(200f, 80f), 100) // Pure vertical drift must still be consumed.
            moveTo(Offset(80f, 60f), 100)
            up()
        }
        waitForIdle()
        assertEquals(1, next)
        assertEquals(0f, nestedY, "Horizontal gesture leaked into parent nested scroll")
        onNodeWithTag("nested-0").performTouchInput {
            down(Offset(280f, 60f))
            moveTo(Offset(280f, 25f), 100) // Vertical ownership must remain with the list.
            moveTo(Offset(80f, 25f), 100)
            up()
        }
        waitForIdle()
        assertEquals(1, next, "Vertical gesture later changed into a row action")
        assertTrue(nestedY > 0f)
        val previousNested = nestedY
        onNodeWithTag("nested-0").performTouchInput {
            down(Offset(280f, 80f))
            moveTo(Offset(220f, 10f), 100) // Both axes exceed slop: vertical is dominant.
            moveTo(Offset(80f, 10f), 100)
            up()
        }
        waitForIdle()
        assertEquals(1, next, "Vertical-dominant diagonal must not claim a row swipe")
        assertTrue(nestedY > previousNested)
    }

    private fun ComposeUiTest.saveSwipe(name: String) {
        val directory = System.getenv("LMUSIC_UI_SCREENSHOT_DIR") ?: return
        val bitmap = onNodeWithTag("row").captureToImage()
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData()?.use { File(File(directory).apply { mkdirs() }, name).writeBytes(it.bytes) }
        }
    }

}
