package com.lalilu.lplayer.components


import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lalilu.extensions.Item
import com.lalilu.extensions.rotationalDiff
import com.lalilu.lmedia.audioPlaybackStatus
import com.lalilu.lmedia.domain.model.AudioPlaybackPresentation
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.action.QueueAction
import com.lalilu.lplayer.playback.PlaybackMode
import com.lalilu.navigation.AppRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 播放列表的输入：列表数据 + 这次变化的成因。
 *
 * [pickedByUser] 来自队列自身（`QueueState.currentPickedByUser`），不是 UI 猜的：
 * 队列左旋 `n-1` 位时，「点击列表最后一行」与「按上一首」得到的新旧列表完全一样，
 * 只有成因能区分两者——前者要把被点中的那首带回视野，后者要原地不动。
 */
data class PlaylistItems(
    val items: List<LAudio>,
    val pickedByUser: Boolean = false,
)

@Composable
fun PlaylistLayout(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 200.dp),
    items: Flow<PlaylistItems>
) {
    var actualItems by remember { mutableStateOf(emptyList<Item<LAudio>>()) }
    val isPlaying = LPlayer.instance.isPlaying.collectAsState(false)
    val pendingNext by LPlayer.instance.pendingNext.collectAsState()
    val mode by LPlayer.instance.playbackMode.collectAsState()

    LaunchedEffect(Unit) {
        items.flowOn(Dispatchers.Default)
            .collect { snapshot ->
                val oldList = actualItems
                // 「正在播放的排在队首」的循环队列：新列表是旧列表的一次左旋，
                // 这里固定采用「切点之前的整块搬到末尾」的语义，避免大幅跳跃时动画方向反转
                val newList = oldList.rotationalDiff(
                    items = snapshot.items,
                    preferForwardRotation = snapshot.pickedByUser,
                    getId = { it.mediaKey.stableKey },
                    isSameItem = { a, b -> a.mediaKey == b.mediaKey },
                    isSameContent = { a, b ->
                        a.id == b.id
                                && a.title == b.title
                                && a.subtitle == b.subtitle
                                && a.mediaSourceName == b.mediaSourceName
                                && a.available == b.available
                                && a.extra == b.extra
                    }
                )
                val newHead = newList.firstOrNull()

                // 若无法获取新列表的首元素，则说明新列表为空，及时返回
                if (newHead == null) {
                    withContext(Dispatchers.Main) { actualItems = emptyList() }
                    return@collect
                }

                withContext(Dispatchers.Main) {
                    val plan = planPlaylistScroll(
                        oldList = oldList,
                        newHead = newHead,
                        visibleKeys = listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet(),
                        pickedByUser = snapshot.pickedByUser,
                    )

                    when (plan) {
                        // 新旧队首都不在屏幕上：不需要滚动
                        PlaylistScrollPlan.KeepScroll -> actualItems = newList

                        // 不插入空列表帧：保留差分留下的行及其已加载封面。
                        PlaylistScrollPlan.JumpToTop -> {
                            updatePlaylistAtTop(
                                oldList, newList, listState,
                                leadingItemCount = { if (pendingNext != null) 1 else 0 },
                            ) { actualItems = it }
                        }
                    }
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
            .clipToBounds(),
        contentPadding = contentPadding,
        overscrollEffect = null
    ) {
        pendingNext?.let { pending ->
            item(key = "pending-next-intent:${pending.playbackId}") {
                SwipeableQueueItem(
                    modifier = Modifier.animateItem(), random = mode == PlaybackMode.SHUFFLE,
                    onPlayNext = { QueueAction.AddToNext(pending).action() },
                    onRemove = { QueueAction.Remove(pending).action() },
                ) {
                    SongCard(
                        id = "pending:${pending.playbackId}", imageData = pending,
                        title = pending.title, subtitle = "待播放 · ${pending.subtitle}",
                        onClick = { PlayerAction.PlayById(pending.playbackId).action() },
                        onLongClick = {},
                    )
                }
            }
        }
        itemsIndexed(
            items = actualItems,
            key = { _, item -> item.key },
        ) { index, item ->
            val bgColor = animateColorAsState(
                if (index == 0 && isPlaying.value) MaterialTheme.colorScheme.onBackground.copy(0.01f)
                else Color.Transparent
            )
            val data = item.data
            val playbackStatus = audioPlaybackStatus(data)
            val failure = playbackStatus as? AudioPlaybackPresentation.Failed

            SwipeableQueueItem(
                modifier = Modifier.animateItem(), random = mode == PlaybackMode.SHUFFLE,
                onPlayNext = { QueueAction.AddToNext(data).action() },
                onRemove = { QueueAction.Remove(data).action() },
            ) {
                SongCard(
                    modifier = Modifier.drawBehind { drawRect(color = bgColor.value) },
                    id = data.playbackId,
                    imageData = data,
                    enabled = playbackStatus != AudioPlaybackPresentation.SourceNotReady,
                    failureReason = failure?.reason?.displayMessage,
                    title = data.title,
                    subtitle = data.subtitle,
                    onClick = { PlayerAction.PlayByKey(data.mediaKey).action() },
                    onLongClick = { sharedMap ->

                        AppRouter.route("/song/detail")
                            .with("mediaId", data.playbackId)
                            .with("song", data)
                            .with("sharedMap", sharedMap)
                            .jump()
                        // 展开播放页底栏由 AppRouter 末尾的 SheetExpandInterceptor 统一处理，
                        // 无需在此手动 bottomSheetState.show()
                    }
                )
            }
        }
    }
}
