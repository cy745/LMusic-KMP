package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 曲目时长域的解析规则。
 *
 * 这一层从播放页时间轴搬到了 Playback：所有展示时长的地方（进度条、迷你播放器、
 * 播放列表、系统通知）都读同一个值，因此策略必须在这里被钉住。
 */
class PlaybackDurationResolverTest {

    private fun audio(id: String, durationMs: Long): LAudio = LAudio(
        id = id,
        title = id,
        subtitle = "artist",
        mediaSourceName = "source",
        extra = mapOf(LAudioExtraKeys.Duration to durationMs.toString()),
    )

    @Test
    fun metadataWinsUntilTheEngineDisagreesEnough() = runTest {
        val item = MutableStateFlow<LAudio?>(audio("a", 180_000L))
        val engine = MutableStateFlow(0L)
        val resolver = PlaybackDurationResolver(item, engine, backgroundScope)
        runCurrent()
        assertEquals(180_000L, resolver.stableDuration.value)

        // 引擎在切歌瞬间发出的 0 不代表时长真的变成了 0
        engine.value = 0L
        runCurrent()
        assertEquals(180_000L, resolver.stableDuration.value)

        // 差异不到 2%：保留元数据，避免引擎抖动让进度条比例跳动
        engine.value = 181_000L
        runCurrent()
        assertEquals(180_000L, resolver.stableDuration.value)

        // 差异足够大：改用引擎时长
        engine.value = 200_000L
        runCurrent()
        assertEquals(200_000L, resolver.stableDuration.value)
    }

    @Test
    fun engineDurationFillsInWhenMetadataIsMissing() = runTest {
        val item = MutableStateFlow<LAudio?>(audio("a", 0L))
        val engine = MutableStateFlow(0L)
        val resolver = PlaybackDurationResolver(item, engine, backgroundScope)
        runCurrent()
        assertEquals(0L, resolver.stableDuration.value)

        engine.value = 123_456L
        runCurrent()
        assertEquals(123_456L, resolver.stableDuration.value)
    }

    @Test
    fun switchingItemRebuildsTheDomainFromMetadata() = runTest {
        val item = MutableStateFlow<LAudio?>(audio("a", 180_000L))
        val engine = MutableStateFlow(0L)
        val resolver = PlaybackDurationResolver(item, engine, backgroundScope)
        runCurrent()
        assertEquals(180_000L, resolver.stableDuration.value)

        item.value = audio("b", 240_000L)
        runCurrent()
        assertEquals(240_000L, resolver.stableDuration.value)

        // 没有元数据时退回当时的引擎值，等引擎上报后再修正
        item.value = audio("c", 0L)
        runCurrent()
        assertEquals(0L, resolver.stableDuration.value)

        engine.value = 300_000L
        runCurrent()
        assertEquals(300_000L, resolver.stableDuration.value)
    }
}
