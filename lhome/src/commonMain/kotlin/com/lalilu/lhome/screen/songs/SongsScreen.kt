package com.lalilu.lhome.screen.songs

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.Options
import com.lalilu.extensions.ItemRecorder
import com.lalilu.extensions.rememberLazyListAnimateScroller
import com.lalilu.extensions.startRecord
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.component.AudioItemCard
import com.lalilu.lhome.viewmodel.SongsState
import com.lalilu.lhome.viewmodel.SongsVM
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.sortable.GroupId
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Destination("/pages/songs")
data class SongsScreen(
    private val title: String? = null,
    private val mediaIds: List<String> = emptyList()
) : Screen, ScreenInfoFactory, ScreenActionFactory, ScreenBarFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { title ?: "歌曲" }
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        return remember {
            emptyList()
        }
    }

    @Composable
    override fun Content() {
        val viewModel: SongsVM = koinInject()

        SongsScreenContent(
            recorder = { viewModel.recorder },
            state = { viewModel.state.value },
            songs = { viewModel.songs.value }
        )
    }
}

@Composable
fun SongsScreenContent(
    state: () -> SongsState,
    songs: () -> SortResult<LAudio>,
    recorder: () -> ItemRecorder,
    keys: () -> Collection<Any> = { emptyList() },
    onClickGroup: (GroupId) -> Unit = {}
) {
    val songs: SortResult<LAudio> = songs()
    val state: SongsState = state()
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val density = LocalDensity.current

    val statusBar = WindowInsets.statusBars
    val statusBarPadding = statusBar.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val listState: LazyListState = rememberLazyListState()
//    val favouriteIds = state("favourite_ids", emptyList<String>())
    val scroller = rememberLazyListAnimateScroller(
        listState = listState,
        keys = keys
    )


    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBarPadding.calculateTopPadding() + 16.dp,
            bottom = navigationBar.calculateBottomPadding() + 12.dp
        )
    ) {
        startRecord(recorder()) {
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "歌曲",
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (state.searchKeyWord.isBlank()) {
                            "共 ${songs.itemList.size} 首歌曲"
                        } else {
                            "搜索: ${state.searchKeyWord} (${songs.itemList.size} 首)"
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                    )
                }
            }

            songs.draw {
                groupId?.let { groupId ->
                    stickyHeaderWithRecord(
                        key = groupId,
                        contentType = "group"
                    ) {
                        SongsScreenStickyHeader(
                            listState = listState,
                            group = groupId,
                            minOffset = { statusBar.getTop(density) },
                            onClickGroup = onClickGroup
                        )
                    }
                }

                itemsIndexedWithRecord(
                    items = items,
                    key = { index, item -> item.id },
                    contentType = { index, item -> item::class }
                ) { index, item ->
                    val extra = extras.getOrNull(index)

                    AudioItemCard(
                        title = item.titleValue(),
                        subtitle = item.subtitleValue(),
                        imageData = item,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    scope.launch {
                                        PlayerAction.UpdateList(
                                            ids = LMedia.instance.get<LAudio>().map(LItem::idValue),
                                            id = item.idValue(),
                                            start = true
                                        ).action()
                                    }
                                },
                                onLongClick = {
                                    val imageLoader = SingletonImageLoader.get(context)
                                    val coverMemoryKey = imageLoader.components.key(item, Options(context))

                                    AppRouter.route("/song/detail")
                                        .with("mediaId", item.idValue())
                                        .with("coverCacheKey", coverMemoryKey)
                                        .jump()
                                }
                            )
                    )

//                    SongCard(
//                        modifier = Modifier.animateItem(),
//                        song = { item },
//                        onClick = {
//                            if (isSelecting()) {
//                                onSelect(item)
//                            } else {
//                                MediaControl.playWithList(
//                                    mediaIds = songs.itemList.map(LSong::id),
//                                    mediaId = item.id
//                                )
//                            }
//                        },
//                        onLongClick = {
//                            if (isSelecting()) {
//                                onSelect(item)
//                            } else {
//                                AppRouter.route("/pages/songs/detail")
//                                    .with("mediaId", item.id)
//                                    .jump()
//                            }
//                        },
//                        onEnterSelect = { onSelect(item) },
//                        isFavour = { favouriteIds.value.contains(item.id) },
//                        isSelected = { isSelected(item) },
//                        prefixContent = { SortExtraPresetUI.Show(extra) }
//                    )
                }
            }
        }
    }
}
