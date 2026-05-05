package com.lalilu.lalbum.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lalilu.RemixIcon
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.DialogWrapper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lalbum.lalbum.generated.resources.Res
import com.lalilu.lalbum.lalbum.generated.resources.album_detail_screen_title
import com.lalilu.lalbum.viewmodel.AlbumDetailAction
import com.lalilu.lalbum.viewmodel.AlbumDetailVM
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.dialog.GroupIdJumperDialog
import com.lalilu.lmedia.dialog.SortPanelDialog
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.navigation.*
import com.lalilu.navigation.smartbar.CancellableInputerBarPanel
import com.lalilu.navigation.smartbar.CancellableScreenBarPanel
import com.lalilu.remixicon.Design
import com.lalilu.remixicon.Editor
import com.lalilu.remixicon.System
import com.lalilu.remixicon.design.editBoxLine
import com.lalilu.remixicon.design.focus3Line
import com.lalilu.remixicon.editor.sortDesc
import com.lalilu.remixicon.system.checkboxMultipleBlankLine
import com.lalilu.remixicon.system.checkboxMultipleLine
import com.lalilu.remixicon.system.menuSearchLine
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

@Destination("/pages/albums/detail")
data class AlbumDetailScreen(
    val albumId: String,
    val album: LAlbum? = null,
    val coverCacheKey: String? = null,
    val sharedMap: Map<String, String> = emptyMap(),
) : Screen, ScreenInfoFactory, ScreenActionFactory, ScreenBarFactory {
    override val key: String = "${super.key}:$albumId"

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { stringResource(Res.string.album_detail_screen_title) }
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = koinViewModel<AlbumDetailVM>()
        val state by vm.state

        return remember {
            listOf(
                ScreenAction.Static(
                    title = { "排序" },
                    icon = { RemixIcon.Editor.sortDesc },
                    color = { Color(0xFF1793FF) },
                    onAction = { vm.intent(AlbumDetailAction.ToggleSortPanel) }
                ),
                ScreenAction.Static(
                    title = { "选择" },
                    icon = { RemixIcon.Design.editBoxLine },
                    color = { Color(0xFF009673) },
                    onAction = {
                        vm.selector.isSelecting.value = true
                        DialogWrapper.dismiss()
                    }
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
                        if (state.searchKeyWord.isNotBlank()) Color.Red else null
                    },
                    onAction = {
                        vm.intent(AlbumDetailAction.ToggleSearcherPanel)
                        DialogWrapper.dismiss()
                    }
                ),
                ScreenAction.Static(
                    title = { "定位当前播放歌曲" },
                    icon = { RemixIcon.Design.focus3Line },
                    color = { Color(0xFF8700FF) },
                    onAction = { vm.intent(AlbumDetailAction.LocaleToPlayingItem) }
                ),
            )
        }
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<AlbumDetailVM>(parameters = { parametersOf(albumId) })

        val state by vm.state
        val songs by vm.songs
        val sortAction = vm.sorter.selectedAction.collectAsState()
        val sortConfig = vm.sorter.sortConfig.collectAsState()

        val album by remember { LMedia.instance.flow<LAlbum>(id = albumId) }
            .collectAsState(album)

        SortPanelDialog(
            isVisible = { state.showSortPanel },
            onDismiss = { vm.intent(AlbumDetailAction.HideSortPanel) },
            supportSortActions = vm.sorter.supportedActions,
            selectedSortAction = { sortAction.value },
            sortConfig = { sortConfig.value },
            onUpdateSortConfig = { vm.intent(AlbumDetailAction.UpdateSortConfig(it)) },
            onSelectSortAction = { vm.intent(AlbumDetailAction.SelectSortAction(it)) }
        )

        GroupIdJumperDialog(
            isVisible = { state.showJumperDialog },
            onDismiss = { vm.intent(AlbumDetailAction.HideJumperDialog) },
            sortResult = songs,
            onSelectItem = { vm.intent(AlbumDetailAction.LocaleToGroupItem(it)) }
        )

        CancellableInputerBarPanel(
            isVisible = { state.showSearcherPanel },
            onDismiss = { vm.intent(AlbumDetailAction.HideSearcherPanel) },
            keyword = { state.searchKeyWord },
            onUpdateKeyword = { vm.intent(AlbumDetailAction.SearchFor(it)) }
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

        AlbumDetailScreenContent(
            songs = songs,
            album = album,
            sharedMap = sharedMap,
            coverCacheKey = coverCacheKey,
            keys = { vm.recorder.list().filterNotNull() },
            recorder = { vm.recorder },
            eventFlow = vm.eventFlow(),
            selector = { vm.selector },
            onClickGroup = { vm.intent(AlbumDetailAction.ToggleJumperDialog) }
        )
    }
}
