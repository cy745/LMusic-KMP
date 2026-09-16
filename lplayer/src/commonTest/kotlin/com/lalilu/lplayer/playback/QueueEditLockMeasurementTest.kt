package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lplayer.action.QueueAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 量一量"一次慢加载会挡住后续操作多久"。
 *
 * 这里刻意使用最慢的加载：`skipTo` 一直挂起直到测试放行，模拟 iOS/电脑版在命令里同步等待引擎
 * （原生等待上限 30s）。安卓不在命令里等，因此同一场景下这些断言不成立——本测试量的是公共
 * 边界本身的行为，不代表所有平台都会卡。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueEditLockMeasurementTest {
    private val a = LAudio(id = "a", mediaSourceName = "local")
    private val b = LAudio(id = "b", mediaSourceName = "local")

    @Test fun aSecondSelectionWaitsForTheFirstSlowLoadInsteadOfReplacingIt() = runTest {
        val player = SlowLoadPlayback(backgroundScope)
        player.updatePlaylist(listOf(a, b), 0, false)
        runCurrent()
        // 建队列本身也会走一次加载；测量从"下一次点歌"开始。
        player.loads.clear()
        player.slowLoads = true

        backgroundScope.launch { player.playAudio(a) }
        runCurrent()
        assertEquals(listOf("a"), player.loads)

        val second = backgroundScope.launch { player.playAudio(b) }
        // 模拟第一次加载耗掉 30 秒：第二次点歌完全没有进展。
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf("a"), player.loads)
        // 第二次点歌仍在等待，不是"取消掉第一次再立刻开始"。
        assertTrue(second.isActive)
        assertEquals(0, player.queue.stateSnapshot().index)

        player.release()
        runCurrent()
        assertEquals(listOf("a", "b"), player.loads)
    }

    @Test fun queueEditsAlsoWaitForASlowLoad() = runTest {
        val player = SlowLoadPlayback(backgroundScope)
        player.updatePlaylist(listOf(a, b), 0, false)
        runCurrent()
        // 建队列本身也会走一次加载；测量从"下一次点歌"开始。
        player.loads.clear()
        player.slowLoads = true

        backgroundScope.launch { player.playAudio(a) }
        runCurrent()
        assertEquals(listOf("a"), player.loads)

        backgroundScope.launch { QueueAction.Remove(b).execute(player) }
        advanceTimeBy(30_000)
        runCurrent()
        // 30 秒里删除请求也进不来：队列仍是两项。
        assertEquals(listOf(a, b), player.queue.stateSnapshot().list)

        player.release()
        runCurrent()
        assertEquals(listOf(a), player.queue.stateSnapshot().list)
    }

    @Test fun theBoundaryIsHeldForTheWholeNativeWaitBudget() = runTest {
        val player = SlowLoadPlayback(backgroundScope)
        runCurrent()
        backgroundScope.launch { player.playAudio(a) }
        runCurrent()

        // iOS 与电脑版的原生等待上限是 30s（+5s 定位确认），整段都在边界内；跳过一轮还有 60s 上限。
        advanceTimeBy(35_000)
        runCurrent()
        assertEquals(listOf("a"), player.loads)

        player.release()
        runCurrent()
        assertEquals(listOf("a"), player.loads)
        assertEquals(0, player.queue.stateSnapshot().index)
    }

    private class SlowLoadPlayback(scope: CoroutineScope) : AbstractPlayback(
        coroutineScope = scope,
        history = PlaybackHistoryImpl(object : HistoryStorage {
            override fun savedPosition() = 0L
            override fun savePosition(position: Long) = Unit
        }),
        audioRepository = object : AudioRepository {
            override fun getAudios() = flowOf(emptyList<LAudio>())
            override fun getAudios(ids: List<String>) = flowOf(emptyList<LAudio>())
            override fun getAudio(id: String) = flowOf<LAudio?>(null)
            override suspend fun clearUnavailableAudio() = Unit
        },
    ) {
        private val gate = CompletableDeferred<Unit>()
        val loads = mutableListOf<String>()

        /** 打开后加载会一直挂起，模拟 iOS/电脑版在命令里等待原生加载。 */
        var slowLoads = false

        fun release() = gate.complete(Unit)

        override fun createEngines(): List<PlaybackEngine> = emptyList()

        override suspend fun skipTo(index: Int, start: Boolean) {
            loads += queue.stateSnapshot().list[index].id
            if (slowLoads) gate.await()
            queue.update { switchTo(index) }
        }

        override suspend fun play() = Unit
        override suspend fun pause() = Unit
        override suspend fun stop() = Unit
        override suspend fun seekTo(positionMs: Long) = Unit
        override fun currentPosition() = 0L
    }
}
