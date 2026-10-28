package com.lalilu.lplayer.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import com.lalilu.extensions.Item
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Rebase off-screen rows without changing visible keys, then let animateItem handle the edit. */
internal suspend fun updatePlaylistAtTop(
    oldItems: List<Item<LAudio>>,
    newItems: List<Item<LAudio>>,
    state: LazyListState,
    leadingItemCount: () -> Int = { 0 },
    publish: (List<Item<LAudio>>) -> Unit,
) {
    val visible = state.layoutInfo.visibleItemsInfo
    val first = visible.firstOrNull()
    val prefix = oldItems.indexOfFirst { it.key == first?.key }
    val selectedKey = oldItems.firstOrNull { it.data.mediaKey == newItems.firstOrNull()?.data?.mediaKey }?.key
    if (prefix > 0 && visible.any { it.key == selectedKey }) {
        val scrollOffset = state.firstVisibleItemScrollOffset
        val rebased = oldItems.drop(prefix)
        publish(rebased)
        // Stable keys automatically rebase the first visible row to index zero at the same offset.
        // Do not scroll here: even a visually equivalent snap would release the fade-out layers.
        snapshotFlow { state.layoutInfo }.first { layout ->
            layout.totalItemsCount == rebased.size + leadingItemCount()
        }
        publish(newItems)
        // The index AND offset remain unchanged, so Compose does not reset its item animator.
        state.requestScrollToItem(0, scrollOffset)
        if (scrollOffset != 0) {
            snapshotFlow { state.layoutInfo }.first { layout ->
                layout.totalItemsCount == newItems.size + leadingItemCount()
            }
            // Only reveal the clipped part of the top row, using normal scrolling (no animator reset).
            // A new user scroll may cancel this child without terminating queue observation.
            coroutineScope { launch { state.animateScrollToItem(0) } }
        }
    } else {
        publish(newItems)
        state.scrollToItem(0)
    }
}
