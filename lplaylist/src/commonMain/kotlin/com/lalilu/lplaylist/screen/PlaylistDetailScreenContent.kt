package com.lalilu.lplaylist.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.lalilu.RemixIcon
import org.jetbrains.compose.resources.vectorResource
import com.lalilu.extensions.*
import com.lalilu.lmedia.component.AudioItemCard
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.sortable.GroupId
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.viewmodel.PlaylistDetailEvent
import com.lalilu.lplaylist.viewmodel.PlaylistDetailVM
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.smartbar.NavigatorHeader
import com.lalilu.state
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaylistDetailScreenContent(
    playlist: LPlaylist? = null,
    songs: SortResult<LAudio> = SortResult.empty(),
    enableDraggable: Boolean = false,
    eventFlow: Flow<PlaylistDetailEvent> = emptyFlow(),
    keys: () -> Collection<Any> = { emptyList() },
    recorder: () -> ItemRecorder,
    selector: () -> ItemSelector<LAudio>,
    onClickGroup: (GroupId) -> Unit = {},
    onUpdatePlaylist: (List<String>) -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars
    val listState: LazyListState = rememberLazyListState()
    val vm = koinViewModel<PlaylistDetailVM>()
    val scope = rememberCoroutineScope()
    val stickyHeaderContentType = remember { "group" }
    val favouriteIds = state("favourite_ids") { emptyList<String>() }
    val scroller = rememberLazyListAnimateScroller(
        listState = listState,
        keys = keys
    )

    val playlistState = remember(songs) { songs.itemList.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState
    ) { from, to ->
        playlistState.toMutableList().apply {
            val toIndex = indexOfFirst { it.id == to.key }
            val fromIndex = indexOfFirst { it.id == from.key }
            if (toIndex < 0 || fromIndex < 0) return@rememberReorderableLazyListState

            add(toIndex, removeAt(fromIndex))
            playlistState.clear()
            playlistState.addAll(this)
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LaunchedEffect(Unit) {
        eventFlow.collectLatest { event ->
            when (event) {
                is PlaylistDetailEvent.ScrollToItem -> {
                    scroller.animateTo(
                        key = event.key,
                        isStickyHeader = { it.contentType == stickyHeaderContentType },
                        offset = { item ->
                            // 若是 sticky header，则滚动到顶部
                            if (item.contentType == stickyHeaderContentType) {
                                return@animateTo -statusBar.getTop(density)
                            }

                            val closestStickyHeaderSize = listState.layoutInfo.visibleItemsInfo
                                .lastOrNull { it.index < item.index && it.contentType == stickyHeaderContentType }
                                ?.size ?: 0

                            -(statusBar.getTop(density) + closestStickyHeaderSize)
                        }
                    )
                }

                else -> {}
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
//            .fadeEdgeForStatusBar(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        startRecord(recorder()) {
            item(key = "HEADER") {
                NavigatorHeader(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth(),
//                        .statusBarsIgnoringVisibilityPadding(),
                    rowExtraSpace = 8.dp,
                    title = playlist?.title ?: "unknown",
                    subTitle = playlist?.subTitle?.takeIf { it.isNotBlank() }
                        ?: "共 ${playlist?.mediaIds?.size ?: 0} 首歌曲",
                ) {
                    IconButton(onClick = {
                        AppRouter.route("/pages/playlist/edit")
                            .with("playlistId", playlist?.id ?: "")
                            .push()
                    }) {
                        Icon(
                            imageVector = vectorResource(RemixIcon.Design.editBoxFill),
                            contentDescription = null
                        )
                    }
                }
            }

            if (enableDraggable) {
                items(
                    items = playlistState,
                    key = { it.id },
                    contentType = { it::class }
                ) { item ->
                    ReorderableItem(
                        state = reorderableState,
                        key = item.id
                    ) { isDragging ->
                        AudioItemCard(
                            modifier = Modifier.fillMaxWidth(),
                            coverModifier = Modifier
                                .draggableHandle(onDragStopped = { onUpdatePlaylist(playlistState.map { it.id }) }),
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            imageData = item,
                            isSelecting = { selector().isSelecting.value },
                            isSelected = { selector().isSelected(item) },
                            onEnterSelect = { selector().onSelect(item) },
                            onSelect = { selector().onSelect(item) },
                            onPlay = {
                                scope.launch {
                                    val list = playlist?.mediaIds ?: emptyList()
                                    PlayerAction.UpdateList(
                                        ids = list,
                                        id = item.id,
                                        start = true
                                    ).action()
                                }
                            },
                            onNavigateToDetail = { sharedMap ->
                                val coverMemoryKey = context.retrieveCacheKey(item)

                                AppRouter.route("/song/detail")
                                    .with("mediaId", item.id)
                                    .with("song", item)
                                    .with("coverCacheKey", coverMemoryKey)
                                    .with("sharedMap", sharedMap)
                                    .jump()
                            }
                        )
                    }
                }
            } else {
                songs.draw {
                    groupId?.let { groupId ->
                        stickyHeader(
                            key = groupId,
                            contentType = stickyHeaderContentType
                        ) {
                            Text(
                                modifier = Modifier.animateItem(),
                                text = groupId.text
                            )
//                            SongsScreenStickyHeader(
//                                modifier = Modifier.animateItem(),
//                                listState = listState,
//                                group = groupId,
//                                minOffset = { statusBar.getTop(density) },
//                                onClickGroup = onClickGroup
//                            )
                        }
                    }

                    itemsIndexed(
                        items = items,
                        key = { index, item -> item.id },
                        contentType = { index, item -> item::class }
                    ) { index, item ->
                        val extra = extras.getOrNull(index)

                        AudioItemCard(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth(),
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            imageData = item,
                            isSelecting = { selector().isSelecting.value },
                            isSelected = { selector().isSelected(item) },
                            onEnterSelect = { selector().onSelect(item) },
                            onSelect = { selector().onSelect(item) },
                            onPlay = {
                                scope.launch {
                                    val list = playlist?.mediaIds ?: emptyList()
                                    PlayerAction.UpdateList(
                                        ids = list,
                                        id = item.id,
                                        start = true
                                    ).action()
                                }
                            },
                            onNavigateToDetail = { sharedMap ->
                                val coverMemoryKey = context.retrieveCacheKey(item)

                                AppRouter.route("/song/detail")
                                    .with("mediaId", item.id)
                                    .with("song", item)
                                    .with("coverCacheKey", coverMemoryKey)
                                    .with("sharedMap", sharedMap)
                                    .jump()
                            }
                        )
                    }
                }
            }
        }

//        smartBarPadding()
    }
}