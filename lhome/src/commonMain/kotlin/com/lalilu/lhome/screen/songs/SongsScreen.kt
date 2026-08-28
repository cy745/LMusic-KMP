package com.lalilu.lhome.screen.songs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.lalilu.RemixIcon
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.*
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.viewmodel.SongsAction
import com.lalilu.lhome.viewmodel.SongsEvent
import com.lalilu.lhome.viewmodel.SongsState
import com.lalilu.lhome.viewmodel.SongsVM
import com.lalilu.lmedia.component.AudioItemCard
import com.lalilu.lmedia.dialog.GroupIdJumperDialog
import com.lalilu.lmedia.dialog.SortPanelDialog
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.sortable.GroupId
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.*
import com.lalilu.navigation.smartbar.CancellableInputerBarPanel
import com.lalilu.navigation.smartbar.CancellableScreenBarPanel
import com.lalilu.navigation.smartbar.NavigatorHeader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import kotlin.time.Duration.Companion.milliseconds

@Destination("/pages/songs")
data class SongsScreen(
    private val title: String? = null,
    private val mediaIds: List<String> = emptyList(),
    private val keyword: String = ""
) : Screen,
    ScreenInfoFactory,
    ScreenActionFactory,
    ScreenBarFactory,
    ScreenViewModelFactory<SongsVM> {

    @Composable
    override fun vm(): SongsVM = koinViewModel(
        parameters = { parametersOf(keyword) }
    )

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { title ?: "歌曲" },
            icon = RemixIcon.Media.musicLine
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = vm()
        val state by vm.state

        return remember {
            val sortAction = ScreenAction.Static(
                title = { "排序" },
                icon = { RemixIcon.Editor.sortDesc },
                color = { Color(0xFF1793FF) },
                onAction = { vm.intent(SongsAction.ToggleSortPanel) }
            )
            val selectAction = ScreenAction.Static(
                title = { "选择" },
                icon = { RemixIcon.Design.editBoxLine },
                color = { Color(0xFF009673) },
                onAction = {
                    vm.selector.isSelecting.value = true
                    DialogWrapper.dismiss()
                }
            )
            val searchAction = ScreenAction.Static(
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
            )
            val locateAction = ScreenAction.Static(
                title = { "定位当前播放歌曲" },
                icon = { RemixIcon.Design.focus3Line },
                color = { Color(0xFF8700FF) },
                onAction = { vm.intent(SongsAction.LocaleToPlayingItem) }
            )
            listOf(
                sortAction,
                selectAction,
                searchAction,
                locateAction,
                sortAction.deepLink("sort"),
                selectAction.deepLink("select"),
                searchAction.deepLink("search"),
                locateAction.deepLink("locate_playing"),
            )
        }
    }

    @Composable
    override fun Content() {
        val vm = vm()
        val audioRepo = org.koin.compose.koinInject<AudioRepository>()
        val songs by vm.songs
        val state by vm.state
        val sortAction = vm.sorter.selectedAction.collectAsState()
        val sortConfig = vm.sorter.sortConfig.collectAsState()

        SortPanelDialog(
            isVisible = { state.showSortPanel },
            onDismiss = { vm.intent(SongsAction.HideSortPanel) },
            supportSortActions = vm.sorter.supportedActions,
            selectedSortAction = { sortAction.value },
            sortConfig = { sortConfig.value },
            onSelectSortAction = { vm.intent(SongsAction.SelectSortAction(it)) },
            onUpdateSortConfig = { vm.intent(SongsAction.UpdateSortConfig(it)) }
        )

        CancellableInputerBarPanel(
            isVisible = { state.showSearcherPanel },
            onDismiss = { vm.intent(SongsAction.HideSearcherPanel) },
            keyword = { state.searchKeyWord },
            onUpdateKeyword = { vm.intent(SongsAction.SearchFor(it)) }
        )

        GroupIdJumperDialog(
            isVisible = { state.showJumperDialog },
            onDismiss = { vm.intent(SongsAction.HideJumperDialog) },
            sortResult = songs,
            onSelectItem = { vm.intent(SongsAction.LocaleToGroupItem(it)) }
        )

        CancellableScreenBarPanel(
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
            audioRepo = audioRepo,
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
    audioRepo: AudioRepository,
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
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

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


    if (songs.itemList.isEmpty()) {
        AnimateVisibleForOnce(
            modifier = Modifier.fillMaxSize(),
            delay = 300.milliseconds
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBarPadding.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp
        )
    ) {
        startRecord(recorder()) {
            item(key = "header") {
                NavigatorHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = "歌曲",
                    subTitle = if (state.searchKeyWord.isBlank()) {
                        "共 ${songs.itemList.size} 首歌曲"
                    } else {
                        "搜索: ${state.searchKeyWord} (${songs.itemList.size} 首)"
                    }
                )
            }

            songs.draw {
                groupId?.let { groupId ->
                    stickyHeader(
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

                itemsIndexed(
                    items = items,
                    key = { index, item -> item.id },
                    contentType = { index, item -> item::class }
                ) { index, item ->
                    val extra = extras.getOrNull(index)

                    AudioItemCard(
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
                        },
                        modifier = Modifier.animateItem()
                            .padding(vertical = 0.5f.dp)
                    )
                }
            }
        }
    }
}
