package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.mediaKey
import kotlin.random.Random

/**
 * 播放队列更新请求，用于构建一次原子性的队列更新。
 *
 * 构造时固定当前 [QueueState] 的快照，后续所有操作均在此快照基础上累积，
 * 内部维护 [pendingList] 和 [pendingIndex] 两个可变状态。
 * 最终通过 [build] 产出新的 [QueueState]。
 *
 * 支持链式调用：
 * ```
 * val req = QueueUpdateRequest(snapshot)
 *     .addToStart(audios)
 *     .addToNext(moreAudios)
 *     .switchTo(0)
 * val newState = req.build(QueueUpdateReason.Inner)
 * ```
 *
 * Callers MUST pre-resolve any [LItem] to [List]<[LAudio]> before calling.
 */
class QueueUpdateRequest(
    snapshot: QueueState
) : QueueMutationOps<QueueUpdateRequest> {
    private var pendingList: List<LAudio> = snapshot.list
    private var pendingIndex: Int = snapshot.index
    val currentIndex: Int get() = normalizeIndex(pendingIndex)

    /** Resolve identity and insertion against the same atomic queue snapshot. */
    fun selectOrInsert(audio: LAudio): QueueUpdateRequest {
        val existing = pendingList.indexOfFirst { it.mediaKey == audio.mediaKey }
        if (existing >= 0) return switchTo(existing)
        val target = if (pendingList.isEmpty()) 0 else currentIndex + 1
        insert(target, listOf(audio))
        return switchTo(target)
    }

    /** Legacy ID-only actions are ignored if the ID is ambiguous across sources. */
    fun removeLegacyId(id: String): QueueUpdateRequest {
        val keys = pendingList.filter { it.id == id }.map { it.mediaKey }.distinct()
        return if (keys.size == 1) removeAll(keys.toSet()) else this
    }

    internal fun prepareSurpriseNext(random: Random = Random.Default): QueueUpdateRequest {
        val candidate = SurpriseQueueOrder.candidateIndex(pendingList.size, pendingIndex, random)
        if (candidate < 0) return this
        val target = SurpriseQueueOrder.nextIndex(pendingList.size, pendingIndex)
        pendingList = pendingList.toMutableList().apply {
            val previous = get(target)
            set(target, get(candidate))
            set(candidate, previous)
        }
        return this
    }

    /**
     * 添加播放项列表到队列开头。当前播放索引会后移。
     */
    override fun addToStart(items: List<LAudio>): QueueUpdateRequest {
        return insert(0, items)
    }

    /**
     * 添加播放项列表到队列末尾。当前播放索引不变。
     */
    override fun addToEnd(items: List<LAudio>): QueueUpdateRequest {
        return insert(pendingList.size, items)
    }

    /**
     * 添加播放项列表到当前播放元素之后。当前播放索引不变。
     */
    override fun addToNext(items: List<LAudio>): QueueUpdateRequest {
        return insert((pendingIndex + 1).coerceIn(0, pendingList.size), items)
    }

    /**
     * 切换当前播放项到指定索引。
     */
    override fun switchTo(index: Int): QueueUpdateRequest {
        if (index in pendingList.indices) {
            pendingIndex = index
        }
        return this
    }

    /**
     * 替换所有播放项。
     *
     * @param items 新的播放项列表
     * @param index 新的当前播放项索引。当值为 -1 时，
     *              自动在 items 中查找与当前播放项 id 匹配的元素位置作为新索引。
     */
    override fun replaceAll(items: List<LAudio>, index: Int): QueueUpdateRequest {
        var targetIndex = index
        if (targetIndex == -1) {
            val currentKey = pendingList.getOrNull(pendingIndex)?.mediaKey
            // Preserve the selected occurrence when the same song appears more than once.
            val occurrence = pendingList.take(pendingIndex.coerceAtLeast(0))
                .count { it.mediaKey == currentKey }
            val matches = items.indices.filter { items[it].mediaKey == currentKey }
            targetIndex = matches.getOrNull(occurrence) ?: matches.firstOrNull() ?: pendingIndex
        }
        pendingList = items
        pendingIndex = normalizeIndex(targetIndex)
        return this
    }

    /** 替换所有播放项，并自动计算当前播放项的位置。 */
    fun replaceAll(items: List<LAudio>) = replaceAll(items, -1)

    /**
     * Remove all occurrences of this song, without touching another source's song.
     */
    override fun remove(item: LAudio): QueueUpdateRequest {
        return removeAll(setOf(item.mediaKey))
    }

    override fun removeAll(keys: Set<MediaKey>): QueueUpdateRequest =
        removeMatching { _, item -> item.mediaKey in keys }

    override fun removeSource(sourceName: String): QueueUpdateRequest =
        removeMatching { _, item -> item.mediaSourceName == sourceName }

    override fun removeAt(index: Int): QueueUpdateRequest =
        removeMatching { position, _ -> position == index }

    private fun removeMatching(predicate: (Int, LAudio) -> Boolean): QueueUpdateRequest {
        val retained = pendingList.indices.filter { !predicate(it, pendingList[it]) }
        // Keep the same occurrence; if removed, select the next survivor, or the last one.
        val next = retained.indexOfFirst { it >= pendingIndex }
            .takeIf { it >= 0 } ?: retained.lastIndex
        pendingList = retained.map(pendingList::get)
        pendingIndex = normalizeIndex(next)
        return this
    }

    override fun insert(index: Int, items: List<LAudio>): QueueUpdateRequest {
        require(index in 0..pendingList.size) { "Invalid insertion index: $index" }
        val hadCurrent = pendingIndex in pendingList.indices
        pendingList = pendingList.toMutableList().apply { addAll(index, items) }
        if (hadCurrent && index <= pendingIndex) pendingIndex += items.size
        pendingIndex = normalizeIndex(pendingIndex)
        return this
    }

    override fun move(from: Int, to: Int): QueueUpdateRequest {
        if (from !in pendingList.indices || to !in pendingList.indices || from == to) return this
        pendingList = pendingList.toMutableList().apply { add(to, removeAt(from)) }
        pendingIndex = when {
            pendingIndex == from -> to
            from < pendingIndex && to >= pendingIndex -> pendingIndex - 1
            from > pendingIndex && to <= pendingIndex -> pendingIndex + 1
            else -> pendingIndex
        }
        return this
    }

    override fun replace(index: Int, item: LAudio): QueueUpdateRequest {
        if (index in pendingList.indices) {
            pendingList = pendingList.toMutableList().apply { set(index, item) }
        }
        return this
    }

    private fun normalizeIndex(index: Int): Int =
        if (pendingList.isEmpty()) 0 else index.coerceIn(pendingList.indices)

    /** 清空所有播放项，索引重置为 0。 */
    override fun clear(): QueueUpdateRequest {
        pendingList = emptyList()
        pendingIndex = 0
        return this
    }

    /**
     * 构建最终的 [QueueState]。
     */
    fun build(updateReason: QueueUpdateReason): QueueState = QueueState(
        list = pendingList,
        index = normalizeIndex(pendingIndex),
        updateReason = updateReason
    )
}
