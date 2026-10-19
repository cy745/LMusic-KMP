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
    fun engineDurationIsTakenWhenTheEngineOwnsTheCurrentItem() = runTest {
        // 缓存命中：新曲目的时长在"曲目切换"这一刻就已经是引擎报出的值了，
        // 只比较"值有没有变过"会把 264487 当成上一首的残留，时长永远显示不出来。
        val item = MutableStateFlow<LAudio?>(audio("a", 0L))
        val engine = MutableStateFlow(0L)
        val engineKey = MutableStateFlow<String?>(null)
        val resolver = PlaybackDurationResolver(item, engine, backgroundScope, engineKey)
        runCurrent()
        assertEquals(0L, resolver.stableDuration.value)

        // 引擎先切到新曲目并报出时长（缓存命中时几乎必然如此），随后曲目流才跟上。
        // 曲目流还没确认之前先不采信：此时无法排除"这是上一首的残留"。
        val next = audio("b", 0L)
        engineKey.value = next.playbackId
        engine.value = 264_487L
        runCurrent()
        assertEquals(0L, resolver.stableDuration.value, "曲目流还没确认，先保持未知")

        // 曲目流确认到同一首：引擎的 id 与曲目一致，直接采信它已经报出的时长
        //（注意时长值本身没有变化，只比较"值变没变"的旧启发式在这里会永远认不出来）
        item.value = next
        runCurrent()
        assertEquals(264_487L, resolver.stableDuration.value, "引擎已加载当前曲目，时长应当采信")
    }

    @Test
    fun engineDurationForAnotherItemIsStillIgnored() = runTest {
        val item = MutableStateFlow<LAudio?>(audio("a", 0L))
        val engine = MutableStateFlow(0L)
        val engineKey = MutableStateFlow<String?>(null)
        val resolver = PlaybackDurationResolver(item, engine, backgroundScope, engineKey)
        runCurrent()

        val next = audio("b", 0L)
        item.value = next
        runCurrent()
        // 引擎还在上一首：即便它报出的值与切曲时相同（无法用"变化"判断），也不能采信
        engineKey.value = audio("a", 0L).playbackId
        engine.value = 180_000L
        runCurrent()
        assertEquals(0L, resolver.stableDuration.value, "引擎加载的不是当前曲目，时长必须保持未知")
    }

    @Test
    fun staleEngineDurationIsNeverReusedForTheNextItem() = runTest {
        val item = MutableStateFlow<LAudio?>(audio("a", 180_000L))
        val engine = MutableStateFlow(180_000L)
        val resolver = PlaybackDurationResolver(item, engine, backgroundScope)
        runCurrent()
        assertEquals(180_000L, resolver.stableDuration.value)

        // 切到一首还没拿到时长的在线曲目，引擎仍停在上一首的值上
        item.value = audio("b", 0L)
        runCurrent()
        assertEquals(0L, resolver.stableDuration.value, "加载中必须显示未知，而不是上一首的时长")

        // 引擎先把时长清零（还没解析出来）
        engine.value = 0L
        runCurrent()
        assertEquals(0L, resolver.stableDuration.value)

        // 解析出真实时长后立刻采用
        engine.value = 150_000L
        runCurrent()
        assertEquals(150_000L, resolver.stableDuration.value)
    }

    @Test
    fun metadataEnrichedForTheSameItemIsAdopted() = runTest {
        val item = MutableStateFlow<LAudio?>(audio("a", 0L))
        val engine = MutableStateFlow(0L)
        val resolver = PlaybackDurationResolver(item, engine, backgroundScope)
        runCurrent()
        assertEquals(0L, resolver.stableDuration.value)

        // 数据源之后补全了同一首曲目的时长
        item.value = audio("a", 200_000L)
        runCurrent()
        assertEquals(200_000L, resolver.stableDuration.value)
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
