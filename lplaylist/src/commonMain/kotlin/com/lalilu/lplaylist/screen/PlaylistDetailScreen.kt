package com.lalilu.lplaylist.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lalilu.RemixIcon
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.DialogWrapper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.dialog.GroupIdJumperDialog
import com.lalilu.lmedia.dialog.SortPanelDialog
import com.lalilu.lmedia.sortable.SortRuleNormal
import com.lalilu.lplaylist.lplaylist.generated.resources.Res
import com.lalilu.lplaylist.lplaylist.generated.resources.playlist_screen_detail
import com.lalilu.lplaylist.viewmodel.PlaylistDetailAction
import com.lalilu.lplaylist.viewmodel.PlaylistDetailVM
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
import com.lalilu.remixicon.system.deleteBinLine
import com.lalilu.remixicon.system.menuSearchLine
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

@Destination("/pages/playlist/detail")
data class PlaylistDetailScreen(
    val playlistId: String
) : Screen, ScreenInfoFactory, ScreenActionFactory, ScreenBarFactory {
    override val key: String = "${super.key}:$playlistId"

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { stringResource(Res.string.playlist_screen_detail) }
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = koinViewModel<PlaylistDetailVM>()
        val state by vm.state

        return remember {
            listOf(
                ScreenAction.Static(
                    title = { "排序" },
                    icon = { RemixIcon.Editor.sortDesc },
                    color = { Color(0xFF1793FF) },
                    onAction = { vm.intent(PlaylistDetailAction.ToggleSortPanel) }
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
                        val keyword = state.searchKeyWord
                        if (keyword.isNotBlank()) Color.Red else null
                    },
                    onAction = {
                        vm.intent(PlaylistDetailAction.ToggleSearcherPanel)
                        DialogWrapper.dismiss()
                    }
                ),
                ScreenAction.Static(
                    title = { "定位当前播放歌曲" },
                    icon = { RemixIcon.Design.focus3Line },
                    color = { Color(0xFF8700FF) },
                    onAction = { vm.intent(PlaylistDetailAction.LocaleToPlayingItem) }
                ),
            )
        }
    }


    @Composable
    override fun Content() {
        val vm = koinViewModel<PlaylistDetailVM>(parameters = { parametersOf(playlistId) })

        val state by vm.state
        val songs by vm.songs
        val playlist by vm.playlist
        val sortAction = vm.sorter.selectedAction.collectAsState()
        val sortConfig = vm.sorter.sortConfig.collectAsState()

        SortPanelDialog(
            isVisible = { state.showSortPanel },
            onDismiss = { vm.intent(PlaylistDetailAction.HideSortPanel) },
            supportSortActions = vm.sorter.supportedActions,
            selectedSortAction = { sortAction.value },
            sortConfig = { sortConfig.value },
            onUpdateSortConfig = { vm.intent(PlaylistDetailAction.UpdateSortConfig(it)) },
            onSelectSortAction = { vm.intent(PlaylistDetailAction.SelectSortAction(it)) }
        )

        GroupIdJumperDialog(
            isVisible = { state.showJumperDialog },
            onDismiss = { vm.intent(PlaylistDetailAction.HideJumperDialog) },
            sortResult = songs,
            onSelectItem = { vm.intent(PlaylistDetailAction.LocaleToGroupItem(it)) }
        )

        CancellableInputerBarPanel(
            isVisible = { state.showSearcherPanel },
            onDismiss = { vm.intent(PlaylistDetailAction.HideSearcherPanel) },
            keyword = { state.searchKeyWord },
            onUpdateKeyword = { vm.intent(PlaylistDetailAction.SearchFor(it)) }
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
                ScreenAction.Static(
                    title = { "删除" },
                    icon = { RemixIcon.System.deleteBinLine },
                    longClick = { true },
                    color = { Color(0xFFF5381D) },
                    onAction = {
                        val ids = vm.selector.selected().map { it.id }
                        vm.intent(PlaylistDetailAction.RemoveItems(ids))
                    }
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

        PlaylistDetailScreenContent(
            songs = songs,
            playlist = playlist,
            enableDraggable = sortAction.value is SortRuleNormal,
            keys = { vm.recorder.list().filterNotNull() },
            recorder = { vm.recorder },
            eventFlow = vm.eventFlow(),
            selector = { vm.selector },
            onClickGroup = { vm.intent(PlaylistDetailAction.ToggleJumperDialog) },
            onUpdatePlaylist = { vm.intent(PlaylistDetailAction.UpdatePlaylist(it)) }
        )
    }
}