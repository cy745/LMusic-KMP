package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 缓冲位置的来源选择：数据源自带缓存进度时用它，否则沿用引擎上报。
 */
class BufferedPositionResolverTest {

    private class FakeSource(private val progress: Flow<Float>?) : MediaDataSource {
        override fun bufferedProgress(song: LAudio): Flow<Float>? = progress
    }

    private fun audio(id: String): LAudio = LAudio(
        id = id,
        title = id,
        subtitle = "artist",
        mediaSourceName = "source",
    )

    @Test
    fun dataSourceProgressOverridesTheEngineValue() = runTest {
        val ratio = MutableStateFlow(0.25f)
        val resolver = BufferedPositionResolver(
            currentItem = MutableStateFlow(audio("a")),
            engineBufferedPosition = MutableStateFlow(5_000L),
            duration = MutableStateFlow(200_000L),
            dataSourceOf = { FakeSource(ratio) },
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(50_000L, resolver.bufferedPosition.value)

        // 缓存继续增长
        ratio.value = 0.75f
        runCurrent()
        assertEquals(150_000L, resolver.bufferedPosition.value)
    }

    @Test
    fun engineValueIsUsedWhenTheDataSourceHasNoProgress() = runTest {
        val resolver = BufferedPositionResolver(
            currentItem = MutableStateFlow(audio("a")),
            engineBufferedPosition = MutableStateFlow(5_000L),
            duration = MutableStateFlow(200_000L),
            dataSourceOf = { FakeSource(null) },
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(5_000L, resolver.bufferedPosition.value)
    }

    @Test
    fun engineValueIsUsedWhileTheDurationIsStillUnknown() = runTest {
        val resolver = BufferedPositionResolver(
            currentItem = MutableStateFlow(audio("a")),
            engineBufferedPosition = MutableStateFlow(5_000L),
            duration = MutableStateFlow(0L),
            dataSourceOf = { FakeSource(MutableStateFlow(0.5f)) },
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(5_000L, resolver.bufferedPosition.value)
    }

    @Test
    fun progressIsClampedToTheDuration() = runTest {
        val resolver = BufferedPositionResolver(
            currentItem = MutableStateFlow(audio("a")),
            engineBufferedPosition = MutableStateFlow(0L),
            duration = MutableStateFlow(100_000L),
            dataSourceOf = { FakeSource(MutableStateFlow(1.4f)) },
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(100_000L, resolver.bufferedPosition.value)
    }

    @Test
    fun lookupFailureFallsBackToTheEngineValue() = runTest {
        val resolver = BufferedPositionResolver(
            currentItem = MutableStateFlow(audio("a")),
            engineBufferedPosition = MutableStateFlow(5_000L),
            duration = MutableStateFlow(200_000L),
            // 数据源解析失败（例如容器未启动）不能让缓冲进度中断
            dataSourceOf = { error("media source registry is not available") },
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(5_000L, resolver.bufferedPosition.value)
    }

    @Test
    fun switchingItemSwitchesTheProgressSource() = runTest {
        val item = MutableStateFlow<LAudio?>(audio("a"))
        val ratio = MutableStateFlow(0.5f)
        val resolver = BufferedPositionResolver(
            currentItem = item,
            engineBufferedPosition = MutableStateFlow(7_000L),
            duration = MutableStateFlow(100_000L),
            dataSourceOf = { song -> if (song.id == "a") FakeSource(ratio) else FakeSource(null) },
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(50_000L, resolver.bufferedPosition.value)

        // 新曲目没有缓存进度能力，回到引擎上报
        item.value = audio("b")
        runCurrent()
        assertEquals(7_000L, resolver.bufferedPosition.value)
    }
}
