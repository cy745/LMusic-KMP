package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [QueueState.currentPickedByUser] 的行为契约。
 *
 * 这条事实的用途：队列左旋 `n-1` 位时，「点击列表最后一行」与「按上一首」得到的新旧列表
 * 完全一样，只有成因能区分——点行时列表应当把被点中的那首带回视野，按上一首时应当原地不动。
 *
 * 判据只能来自队列命令边界（`selectOrInsert` 是「播放某一项」的唯一入口），
 * 用例名即效果描述。
 */
class QueueCurrentPickTest {

    private val a = LAudio(id = "a", mediaSourceName = "local")
    private val b = LAudio(id = "b", mediaSourceName = "local")
    private val c = LAudio(id = "c", mediaSourceName = "local")

    @Test
    fun selectingAnItemMarksItAsPickedByUser() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), -1) }

        queue.update { selectOrInsert(b) }

        assertEquals(true, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun steppingToAnotherSlotIsNotAPick() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), -1) }

        queue.update { switchTo(2) }

        assertEquals(false, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun reselectingTheSameSlotIsStillAPick() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), 1) }

        queue.update { selectOrInsert(b) }

        assertEquals(true, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun explicitReplaceWithStartIndexIsAPick() = runTest {
        val queue = PlayableQueueImpl()

        queue.update { replaceAll(listOf(a, b, c), 2) }

        assertEquals(true, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun platformMirrorAdvancingTheIndexIsNotAPick() = runTest {
        // 自动播放下一首：播放器自己在推进，只是通过镜像回报
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), 0) }

        queue.update(QueueUpdateReason.Sync) { replaceAll(listOf(a, b, c), 1) }

        assertEquals(false, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun platformMirrorAcknowledgingTheSameSlotKeepsThePick() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), -1) }
        queue.update { selectOrInsert(c) }

        queue.update(QueueUpdateReason.Sync) { replaceAll(queue.stateSnapshot().list, 2) }

        assertEquals(true, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun historyRestoreAdvancingTheIndexIsNotAPick() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), 0) }

        queue.update(QueueUpdateReason.HistoryRestore) { replaceAll(listOf(a, b, c), 1) }

        assertEquals(false, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun stepAfterAPickDoesNotRewriteTheOrigin() = runTest {
        // 共用实现里的 playAudio = selectOrInsert + skipTo，而各平台 skipTo 都会再走一次
        // switchTo(同一索引)。这一步不能把「用户选中」改写成「步进」。
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), -1) }
        queue.update { selectOrInsert(c) }

        queue.update { switchTo(queue.stateSnapshot().index) }

        assertEquals(true, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun removingTheCurrentItemDropsThePick() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), -1) }
        queue.update { selectOrInsert(b) }

        queue.update { remove(b) }

        assertEquals(false, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun contentRefreshWithoutIndexChangeKeepsThePick() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), -1) }
        queue.update { selectOrInsert(b) }

        queue.update(QueueUpdateReason.Sync) {
            replace(1, b.copy(title = "refreshed"))
        }

        assertEquals(true, queue.stateSnapshot().currentPickedByUser)
    }

    @Test
    fun queueEditBeforeTheCurrentItemKeepsThePick() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(a, b, c), -1) }
        queue.update { selectOrInsert(b) }

        // 在当前项之前插入：索引后移，但当前项没变，成因应当保留
        queue.update { addToStart(listOf(LAudio(id = "z", mediaSourceName = "local"))) }

        val state = queue.stateSnapshot()
        assertEquals(true, state.currentPickedByUser)
        assertEquals(b.mediaKey, state.currentItem()?.mediaKey)
    }
}
