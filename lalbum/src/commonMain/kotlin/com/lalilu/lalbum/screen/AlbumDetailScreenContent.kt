package com.lalilu.lalbum.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.lalilu.common.ext.md5
import com.lalilu.extensions.*
import com.lalilu.lalbum.viewmodel.AlbumDetailEvent
import com.lalilu.lmedia.component.AudioItemCard
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.sortable.GroupId
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import com.lalilu.packed.CoverTitleHeader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
internal fun AlbumDetailScreenContent(
    album: LAlbum? = null,
    songs: SortResult<LAudio> = SortResult.empty(),
    sharedMap: SharedMap = emptyMap(),
    coverCacheKey: String? = null,
    eventFlow: Flow<AlbumDetailEvent> = emptyFlow(),
    keys: () -> Collection<Any> = { emptyList() },
    recorder: () -> ItemRecorder,
    selector: () -> ItemSelector<LAudio>,
    onClickGroup: (GroupId) -> Unit = {},
    onClickAddToPlaylist: () -> Unit = {},
    onClickPlayAll: () -> Unit = {},
) = SharedContext(sharedMap) {
    val sharedMapPrefix = remember { "${album?.id?.md5()}:" }
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val stickyHeaderContentType = remember { "group" }
    val scroller = rememberLazyListAnimateScroller(
        listState = listState,
        keys = keys
    )

    val statusBar = WindowInsets.statusBars
    val statusBarPadding = statusBar.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
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

    val coverData = remember(album) {
        ImageRequest.Builder(context)
            .placeholderMemoryCacheKey(coverCacheKey)
            .data(album)
            .build()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
        contentPadding = PaddingValues(bottom = smartBarHeight() + 16.dp),
    ) {
        startRecord(recorder()) {
            item {
                CoverTitleHeader(
                    coverData = coverData,
                    title = album?.title ?: "Unknown Album",
                    subtitle = album?.subtitle?.takeIf { it.isNotBlank() },
                    extraContent = {
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onClickAddToPlaylist,
                                colors = ButtonDefaults.elevatedButtonColors(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.08f)),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(text = "添加到播放列表")
                            }
                            TextButton(
                                onClick = onClickPlayAll,
                                colors = ButtonDefaults.elevatedButtonColors(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.08f)),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(text = "播放全部")
                            }
                        }

                        if (songs.itemList.isNotEmpty()) {
                            val tips = remember(songs) {
                                val sumDuration = songs.itemList.sumOf { song -> song.metadata.duration }
                                    .toDuration(DurationUnit.MILLISECONDS)
                                val sumDurationStr = HumanReadable.duration(sumDuration)
                                "共 ${songs.itemList.size} 首歌曲 · 总时长 $sumDurationStr"
                            }
                            Text(
                                modifier = Modifier.padding(bottom = 16.dp)
                                    .alpha(0.6f),
                                text = tips,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                            )
                        }
                    }
                )
            }

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
                    }
                }

                itemsIndexed(
                    items = items,
                    key = { index, item -> item.id },
                    contentType = { index, item -> item::class }
                ) { index, item ->
                    AudioItemCard(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth(),
                        sharedMapPrefix = sharedMapPrefix,
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
                                PlayerAction.UpdateList(
                                    ids = songs.itemList.map { it.id },
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
}
