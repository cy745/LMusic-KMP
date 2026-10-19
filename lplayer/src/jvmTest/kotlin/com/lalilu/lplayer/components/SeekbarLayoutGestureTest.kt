package com.lalilu.lplayer.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.lalilu.lplayer.screen.PlaybackTimelineState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 进度条拖动与提交的端到端手势验证。
 *
 * 这里覆盖的是最容易出错的一环：按下、拖动、松手三个阶段的先后顺序必须让"提交 seek"
 * 恰好发生一次，而单纯点击不得提交任何位置。
 */
@OptIn(ExperimentalTestApi::class)
class SeekbarLayoutGestureTest {

    private fun timeline(): PlaybackTimelineState =
        PlaybackTimelineState(initialPositionMs = 0L).also {
            it.onMediaChanged("a", metadataDurationMs = 200_000L)
            it.accept(enginePositionMs = 0L, isPlaying = false, dtMs = 16L)
        }

    @Test
    fun draggingToTheRightCommitsExactlyOneSeek() = runComposeUiTest {
        val timeline = timeline()
        val commits = mutableListOf<Long>()

        setContent {
            Box(modifier = Modifier.size(width = 300.dp, height = 56.dp)) {
                SeekbarLayout(
                    modifier = Modifier.testTag("seekbar"),
                    positionMs = { timeline.positionMs },
                    durationMs = { timeline.durationMs },
                    progress = { timeline.progress },
                    onSeekStart = timeline::onDragStart,
                    onSeekDrag = timeline::onDrag,
                    onSeekCancel = timeline::onDragCancel,
                    onSeek = {
                        commits += it
                        timeline.onSeek(it)
                    },
                )
            }
        }

        onNodeWithTag("seekbar").performTouchInput {
            down(center)
            // 第一次移动会被触摸斜率吃掉一部分，因此随后给一个足够大的位移，
            // 让断言只依赖"位移换算成时间"这一条规则。
            moveBy(Offset(24f, 0f))
            moveBy(Offset(width / 2f, 0f))
            up()
        }
        waitForIdle()

        assertEquals(1, commits.size, "一次拖动只允许提交一次 seek")
        assertTrue(
            commits.single() in 95_000L..110_000L,
            "提交位置应约等于拖动距离换算出的时间，实际为 ${commits.single()}",
        )
        assertFalse(timeline.isDragging)
    }

    @Test
    fun tappingWithoutDraggingCommitsNothing() = runComposeUiTest {
        val timeline = timeline()
        val commits = mutableListOf<Long>()

        setContent {
            Box(modifier = Modifier.size(width = 300.dp, height = 56.dp)) {
                SeekbarLayout(
                    modifier = Modifier.testTag("seekbar"),
                    positionMs = { timeline.positionMs },
                    durationMs = { timeline.durationMs },
                    progress = { timeline.progress },
                    onSeekStart = timeline::onDragStart,
                    onSeekDrag = timeline::onDrag,
                    onSeekCancel = timeline::onDragCancel,
                    onSeek = {
                        commits += it
                        timeline.onSeek(it)
                    },
                )
            }
        }

        onNodeWithTag("seekbar").performTouchInput {
            down(center)
            up()
        }
        waitForIdle()

        assertEquals(emptyList(), commits, "单纯点击不得提交 seek")
        assertFalse(timeline.isDragging, "松手后必须把时间交还给时间轴")
    }

    @Test
    fun unknownDurationKeepsTheBarUndraggable() = runComposeUiTest {
        val timeline = PlaybackTimelineState(initialPositionMs = 0L)
        timeline.onMediaChanged("a", metadataDurationMs = 0L)
        val commits = mutableListOf<Long>()

        setContent {
            Box(modifier = Modifier.size(width = 300.dp, height = 56.dp)) {
                SeekbarLayout(
                    modifier = Modifier.testTag("seekbar"),
                    positionMs = { timeline.positionMs },
                    durationMs = { timeline.durationMs },
                    progress = { timeline.progress },
                    onSeekStart = timeline::onDragStart,
                    onSeekDrag = timeline::onDrag,
                    onSeekCancel = timeline::onDragCancel,
                    onSeek = {
                        commits += it
                        timeline.onSeek(it)
                    },
                )
            }
        }

        onNodeWithTag("seekbar").performTouchInput {
            down(center)
            moveBy(Offset(24f, 0f))
            moveBy(Offset(width / 4f, 0f))
            up()
        }
        waitForIdle()

        assertEquals(emptyList(), commits, "时长未知时不允许提交 seek")
        assertFalse(timeline.isDragging)
    }
}
