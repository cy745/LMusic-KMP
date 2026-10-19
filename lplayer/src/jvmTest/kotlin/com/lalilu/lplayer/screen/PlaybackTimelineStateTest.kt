package com.lalilu.lplayer.screen

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 时间轴的纯逻辑测试：不依赖 Compose 组合，只喂采样与手势。
 */
class PlaybackTimelineStateTest {

    private fun PlaybackTimelineState.feed(
        frames: Int,
        startMs: Long,
        stepMs: Long = 16L,
    ) {
        var position = startMs
        repeat(frames) {
            accept(enginePositionMs = position, isPlaying = true, dtMs = stepMs)
            position += stepMs
        }
    }

    private fun timelineOf(
        durationMs: Long = 200_000L,
        key: Any? = "a",
    ): PlaybackTimelineState = PlaybackTimelineState(initialPositionMs = 0L)
        .also { it.onMediaChanged(key, metadataDurationMs = durationMs) }

    @Test
    fun timelineKeyStaysCompatibleWithTheLyricContentKey() {
        val audio = LAudio(
            id = "audio-id",
            title = "Title",
            subtitle = "Artist",
            mediaSourceName = "source",
        )

        // 歌词内容的 key 是 LAudio.id（见 PlayerLyricContentTest），时间轴必须用同一个值。
        assertEquals(audio.id, timelineKeyOf(audio))
        // 防呆：source-qualified 的 mediaKey 与歌词 key 不是同一个值。
        assertNotEquals(audio.mediaKey, timelineKeyOf(audio))
    }

    @Test
    fun steadyPlaybackTracksTheEngineWithoutLag() {
        val timeline = timelineOf()
        timeline.feed(frames = 60, startMs = 0L)

        // 最后一帧推入的位置是 59 * 16，稳态下显示值必须与它完全一致。
        assertEquals(59 * 16L, timeline.positionMs)
        // 比例在稳态允许亚像素级滞后（约 0.07% 宽度）。
        assertTrue(abs(timeline.progress - 59 * 16f / 200_000f) < 0.001f)
    }

    @Test
    fun smallEngineJumpsAreAdoptedImmediately() {
        val timeline = timelineOf()
        timeline.feed(frames = 60, startMs = 0L)

        // 100ms 的偏差在容差内，属于"引擎正常前进"，不应触发过渡。
        timeline.accept(enginePositionMs = 1_044L, isPlaying = true, dtMs = 16L)
        assertEquals(1_044L, timeline.positionMs)
    }

    @Test
    fun seekCommitNeverRegressesWhileTheEngineIsLate() {
        val timeline = timelineOf()
        timeline.feed(frames = 10, startMs = 0L)
        val releasedAt = timeline.positionMs

        timeline.onDragStart(releasedAt)
        timeline.onDrag(50_000L)
        assertEquals(50_000L, timeline.positionMs)

        timeline.onSeek(50_000L)
        assertFalse(timeline.isDragging)

        // 引擎还没动：显示值必须停在目标附近，绝不回退到旧位置。
        repeat(5) {
            timeline.accept(enginePositionMs = releasedAt + 16L, isPlaying = true, dtMs = 16L)
            assertTrue(timeline.positionMs >= 50_000L, "提交期间不允许回退")
        }

        // 引擎落地。
        timeline.accept(enginePositionMs = 50_000L, isPlaying = true, dtMs = 16L)
        assertEquals(50_000L, timeline.positionMs)

        // 落地后继续正常跟随。
        timeline.accept(enginePositionMs = 50_016L, isPlaying = true, dtMs = 16L)
        assertEquals(50_016L, timeline.positionMs)
    }

    @Test
    fun draggingFreezesTheTimelineUntilRelease() {
        val timeline = timelineOf()
        timeline.feed(frames = 10, startMs = 0L)

        timeline.onDragStart(timeline.positionMs)
        timeline.onDrag(30_000L)

        repeat(5) { timeline.accept(enginePositionMs = 1_000L + it * 16L, isPlaying = true, dtMs = 16L) }
        assertEquals(30_000L, timeline.positionMs)

        // 取消拖动：残差保留，随后平滑回到真实播放位置。
        timeline.onDragCancel()
        timeline.feed(frames = 60, startMs = 1_100L)
        assertTrue(abs(timeline.positionMs - (1_100L + 59 * 16L)) < 20L)
    }

    @Test
    fun mediaSwitchSlidesInsteadOfJumping() {
        val timeline = timelineOf(durationMs = 200_000L, key = "a")
        timeline.feed(frames = 600, startMs = 0L)
        val beforeSwitch = timeline.positionMs

        timeline.onMediaChanged("b", metadataDurationMs = 180_000L)
        // 时长与媒体身份原子切换。
        assertEquals(180_000L, timeline.durationMs)

        // 引擎重置到新歌：第一帧只允许移动很小一部分，而不是瞬间归零。
        timeline.accept(enginePositionMs = 0L, isPlaying = true, dtMs = 16L)
        val firstFrameMove = beforeSwitch - timeline.positionMs
        assertTrue(
            firstFrameMove in 0..(beforeSwitch / 20),
            "切歌首帧位移过大：$beforeSwitch -> ${timeline.positionMs}",
        )

        // 随后平滑滑向新歌的真实位置。
        timeline.feed(frames = 60, startMs = 16L)
        assertEquals(16L + 59 * 16L, timeline.positionMs)
    }

    @Test
    fun grabbingTheBarDuringAPendingCommitTakesOverFromTheFinger() {
        val timeline = timelineOf()
        timeline.feed(frames = 10, startMs = 0L)
        timeline.onDragStart(timeline.positionMs)
        timeline.onDrag(60_000L)
        timeline.onSeek(60_000L)

        // 提交还没落地，用户又抓住了进度条
        timeline.onDragStart(60_000L)
        timeline.onDrag(20_000L)
        timeline.accept(enginePositionMs = 200L, isPlaying = true, dtMs = 16L)

        assertEquals(20_000L, timeline.positionMs, "手指必须重新拥有时间控制权")
    }

    @Test
    fun durationNeverDegradesToZeroWithinTheSameMedia() {
        val timeline = timelineOf(durationMs = 180_000L)

        timeline.updateDuration(0L)
        assertEquals(180_000L, timeline.durationMs)

        // 与元数据相差不到 2% 时保留元数据，避免引擎抖动导致进度条跳动。
        timeline.updateDuration(181_000L)
        assertEquals(180_000L, timeline.durationMs)

        // 差异足够大时改用引擎时长。
        timeline.updateDuration(200_000L)
        assertEquals(200_000L, timeline.durationMs)
    }

    @Test
    fun unknownDurationDisablesSeeking() {
        val timeline = timelineOf(durationMs = 0L)
        assertFalse(timeline.canSeek)

        timeline.onDragStart(1_000L)
        assertFalse(timeline.isDragging)
        assertEquals(0f, timeline.progress)
    }

    @Test
    fun progressStaysContinuousWhenDurationChangesMidMedia() {
        val timeline = timelineOf(durationMs = 200_000L)
        timeline.feed(frames = 300, startMs = 0L)
        val progressBefore = timeline.progress

        // 同媒体内时长被修正：比例只能平滑变化，不能瞬间跳。
        timeline.updateDuration(240_000L)
        timeline.accept(enginePositionMs = 300 * 16L, isPlaying = true, dtMs = 16L)
        assertTrue(
            abs(timeline.progress - progressBefore) < 0.02f,
            "时长修正不应造成比例跳变：$progressBefore -> ${timeline.progress}",
        )

        // 收敛后比例回到真实值。
        timeline.feed(frames = 120, startMs = 300 * 16L + 16L)
        val expected = timeline.positionMs.toFloat() / 240_000f
        assertTrue(abs(timeline.progress - expected) < 0.001f)
    }
}
