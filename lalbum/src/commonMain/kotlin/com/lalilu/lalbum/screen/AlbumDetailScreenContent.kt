package com.lalilu.lalbum.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.Options
import com.lalilu.extensions.ItemRecorder
import com.lalilu.extensions.ItemSelector
import com.lalilu.extensions.rememberLazyListAnimateScroller
import com.lalilu.extensions.startRecord
import com.lalilu.lmedia.component.AudioItemCard
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.sortable.GroupId
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lalbum.viewmodel.AlbumDetailEvent
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.smartbar.NavigatorHeader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
internal fun AlbumDetailScreenContent(
    album: LAlbum? = null,
    songs: SortResult<LAudio> = SortResult.empty(),
    eventFlow: Flow<AlbumDetailEvent> = emptyFlow(),
    keys: () -> Collection<Any> = { emptyList() },
    recorder: () -> ItemRecorder,
    selector: () -> ItemSelector<LAudio>,
    onClickGroup: (GroupId) -> Unit = {}
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val stickyHeaderContentType = remember { "group" }
    val scroller = rememberLazyListAnimateScroller(
        listState = listState,
        keys = keys
    )

    LaunchedEffect(Unit) {
        eventFlow.collectLatest { event ->
            when (event) {
                is AlbumDetailEvent.ScrollToItem -> {
                    scroller.animateTo(
                        key = event.key,
                        isStickyHeader = { it.contentType == stickyHeaderContentType },
                        offset = { item ->
                            if (item.contentType == stickyHeaderContentType) {
                                return@animateTo -statusBar.getTop(density)
                            }

                            val closestStickyHeaderSize = listState.layoutInfo.visibleItemsInfo
                                .lastOrNull {
                                    it.index < item.index && it.contentType == stickyHeaderContentType
                                }
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
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        startRecord(recorder()) {
            itemWithRecord(key = "HEADER") {
                NavigatorHeader(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth(),
                    rowExtraSpace = 8.dp,
                    title = album?.title ?: "Unknown Album",
                    subTitle = album?.subtitle?.takeIf { it.isNotBlank() }
                        ?: "${songs.itemList.size} songs"
                )
            }

            songs.draw {
                groupId?.let { groupId ->
                    stickyHeaderWithRecord(
                        key = groupId,
                        contentType = stickyHeaderContentType
                    ) {
                        Text(
                            modifier = Modifier.animateItem(),
                            text = groupId.text
                        )
                    }
                }

                itemsIndexedWithRecord(
                    items = items,
                    key = { index, item -> item.id },
                    contentType = { index, item -> item::class }
                ) { index, item ->
                    AudioItemCard(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth(),
                        title = item.titleValue(),
                        subtitle = item.subtitleValue(),
                        imageData = item,
                        isSelecting = { selector().isSelecting.value },
                        isSelected = { selector().isSelected(item) },
                        onEnterSelect = { selector().onSelect(item) },
                        onSelect = { selector().onSelect(item) },
                        onPlay = {
                            scope.launch {
                                PlayerAction.UpdateList(
                                    ids = songs.itemList.map { it.idValue() },
                                    id = item.idValue(),
                                    start = true
                                ).action()
                            }
                        },
                        onNavigateToDetail = {
                            val imageLoader = SingletonImageLoader.get(context)
                            val coverMemoryKey = imageLoader.components.key(item, Options(context))

                            AppRouter.route("/song/detail")
                                .with("mediaId", item.idValue())
                                .with("song", item)
                                .with("coverCacheKey", coverMemoryKey)
                                .jump()
                        }
                    )
                }
            }
        }
    }
}
