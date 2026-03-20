package com.lalilu.lhome.screen.songs

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.Options
import com.lalilu.RemixIcon
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.*
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.component.AudioItemCard
import com.lalilu.lhome.screen.dialog.SongsHeaderJumperDialog
import com.lalilu.lhome.screen.dialog.SongsSortPanelDialog
import com.lalilu.lhome.viewmodel.SongsAction
import com.lalilu.lhome.viewmodel.SongsEvent
import com.lalilu.lhome.viewmodel.SongsState
import com.lalilu.lhome.viewmodel.SongsVM
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.sortable.GroupId
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.*
import com.lalilu.remixicon.Design
import com.lalilu.remixicon.Editor
import com.lalilu.remixicon.System
import com.lalilu.remixicon.design.editBoxLine
import com.lalilu.remixicon.design.focus3Line
import com.lalilu.remixicon.editor.sortDesc
import com.lalilu.remixicon.system.checkboxMultipleBlankLine
import com.lalilu.remixicon.system.checkboxMultipleLine
import com.lalilu.remixicon.system.menuSearchLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

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
        val vm: SongsVM = koinScreenViewModel()
        val state by vm.state

        return remember {
            listOf(
                ScreenAction.Static(
                    title = { "排序" },
                    icon = { RemixIcon.Editor.sortDesc },
                    color = { Color(0xFF1793FF) },
                    onAction = { vm.intent(SongsAction.ToggleSortPanel) }
                ),
                ScreenAction.Static(
                    title = { "选择" },
                    icon = { RemixIcon.Design.editBoxLine },
                    color = { Color(0xFF009673) },
                    onAction = { vm.selector.isSelecting.value = true }
                ),
                ScreenAction.Static(
                    title = { "搜索" },
                    subTitle = {
                        val keyword = state.searchKeyWord
                        if (keyword.isNotBlank()) "搜索中： $keyword" else null
                    },
                    icon = { RemixIcon.System.menuSearchLine },
                    color = { Color(0xFF8BC34A) },
                    dotColor = {
                        val keyword = state.searchKeyWord
                        if (keyword.isNotBlank()) Color.Red else null
                    },
                    onAction = {
                        vm.intent(SongsAction.ToggleSearcherPanel)
                        DialogWrapper.dismiss()
                    }
                ),
                ScreenAction.Static(
                    title = { "定位当前播放歌曲" },
                    icon = { RemixIcon.Design.focus3Line },
                    color = { Color(0xFF8700FF) },
                    onAction = { vm.intent(SongsAction.LocaleToPlayingItem) }
                ),
            )
        }
    }

    @Composable
    override fun Content() {
        val vm: SongsVM = koinScreenViewModel()
        val songs by vm.songs
        val state by vm.state
        val sortAction = vm.sorter.selectedAction.collectAsState()
        val sortConfig = vm.sorter.sortConfig.collectAsState()

        SongsSortPanelDialog(
            isVisible = { state.showSortPanel },
            onDismiss = { vm.intent(SongsAction.HideSortPanel) },
            supportSortActions = vm.sorter.supportedActions,
            selectedSortAction = { sortAction.value },
            sortConfig = { sortConfig.value },
            onSelectSortAction = { vm.intent(SongsAction.SelectSortAction(it)) },
            onUpdateSortConfig = { vm.intent(SongsAction.UpdateSortConfig(it)) }
        )

        SongsSearcherPanel(
            isVisible = { state.showSearcherPanel },
            onDismiss = { vm.intent(SongsAction.HideSearcherPanel) },
            keyword = { state.searchKeyWord },
            onUpdateKeyword = { vm.intent(SongsAction.SearchFor(it)) }
        )

        SongsHeaderJumperDialog(
            isVisible = { state.showJumperDialog },
            onDismiss = { vm.intent(SongsAction.HideJumperDialog) },
            sortResult = songs,
            onSelectItem = { vm.intent(SongsAction.LocaleToGroupItem(it)) }
        )

        SongsSelectorPanel(
            isVisible = { vm.selector.isSelecting.value },
            onDismiss = { vm.selector.isSelecting.value = false },
            screenActions = listOfNotNull(
                ScreenAction.Static(
                    title = { "全选" },
                    color = { Color(0xFF00ACF0) },
                    icon = { RemixIcon.System.checkboxMultipleLine },
                    onAction = { vm.selector.selectAll(songs.itemList) }
                ),
                ScreenAction.Static(
                    title = { "取消全选" },
                    icon = { RemixIcon.System.checkboxMultipleBlankLine },
                    color = { Color(0xFFFF5100) },
                    onAction = { vm.selector.clear() }
                ),
                requestFor<ScreenAction>(
                    qualifier = named("add_to_favourite_action"),
                    parameters = { parametersOf(vm.selector::selected) }
                ),
                requestFor<ScreenAction>(
                    qualifier = named("add_to_playlist_action"),
                    parameters = { parametersOf(vm.selector::selected) }
                )
            )
        )

        SongsScreenContent(
            recorder = { vm.recorder },
            selector = { vm.selector },
            state = { state },
            songs = { songs },
            eventFlow = { vm.eventFlow() },
            onClickGroup = { vm.intent(SongsAction.ToggleJumperDialog) }
        )
    }
}

@Composable
fun SongsScreenContent(
    state: () -> SongsState,
    songs: () -> SortResult<LAudio>,
    recorder: () -> ItemRecorder,
    selector: () -> ItemSelector<LAudio>,
    eventFlow: () -> Flow<SongsEvent> = { emptyFlow() },
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

    LaunchedEffect(Unit) {
        eventFlow().collectLatest { event ->
            when (event) {
                is SongsEvent.ScrollToItem -> {
                    scroller.animateTo(
                        key = event.key,
                        isStickyHeader = { it.contentType == "group" },
                        offset = { item ->
                            // 若是 sticky header，则滚动到顶部
                            if (item.contentType == "group") {
                                return@animateTo -statusBar.getTop(density)
                            }

                            val closestStickyHeaderSize = listState.layoutInfo.visibleItemsInfo
                                .lastOrNull { it.index < item.index && it.contentType == "group" }
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
                        modifier = Modifier
                            .animateItem()
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
                            .padding(horizontal = 16.dp, vertical = 4.dp)
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
