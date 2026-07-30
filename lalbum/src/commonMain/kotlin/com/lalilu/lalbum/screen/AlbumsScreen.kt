package com.lalilu.lalbum.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import coil3.compose.LocalPlatformContext
import com.lalilu.RemixIcon
import com.lalilu.extensions.retrieveCacheKey
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lalbum.lalbum.generated.resources.Res
import com.lalilu.lalbum.lalbum.generated.resources.album_screen_title
import com.lalilu.lalbum.viewmodel.AlbumsAction
import com.lalilu.lalbum.viewmodel.AlbumsVM
import com.lalilu.lmedia.dialog.SortPanelDialog
import com.lalilu.navigation.*
import com.lalilu.navigation.smartbar.CancellableInputerBarPanel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Destination("/pages/albums")
data object AlbumsScreen : Screen, ScreenInfoFactory, ScreenActionFactory, ScreenBarFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { stringResource(Res.string.album_screen_title) },
            icon = RemixIcon.Media.albumFill
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = koinViewModel<AlbumsVM>()
        val state by vm.state

        return remember {
            listOf(
                ScreenAction.Static(
                    title = { if (state.showText) "隐藏专辑名" else "显示专辑名" },
                    color = { Color(0xFF6E4AC3) },
                    icon = { if (state.showText) RemixIcon.Editor.text else RemixIcon.Editor.formatClear },
                    onAction = { vm.intent(AlbumsAction.ToggleShowText) }
                ),
                ScreenAction.Static(
                    title = { "排序" },
                    icon = { RemixIcon.Editor.sortDesc },
                    color = { Color(0xFF1793FF) },
                    onAction = { vm.intent(AlbumsAction.ToggleSortPanel) }
                ),
                ScreenAction.Static(
                    title = { "搜索" },
                    subTitle = {
                        val keyword = state.searchKeyWord
                        if (keyword.isNotBlank()) "搜索中： $keyword" else null
                    },
                    icon = { RemixIcon.System.search2Line },
                    color = { Color(0xFF8BC34A) },
                    dotColor = {
                        if (state.searchKeyWord.isNotBlank()) Color.Red else null
                    },
                    onAction = { vm.intent(AlbumsAction.ToggleSearcherPanel) }
                ),
            )
        }
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<AlbumsVM>()

        val state by vm.state
        val albums by vm.albums
        val sortAction = vm.sorter.selectedAction.collectAsState()
        val sortConfig = vm.sorter.sortConfig.collectAsState()
        val context = LocalPlatformContext.current

        SortPanelDialog(
            isVisible = { state.showSortPanel },
            onDismiss = { vm.intent(AlbumsAction.HideSortPanel) },
            supportSortActions = vm.sorter.supportedActions,
            selectedSortAction = { sortAction.value },
            sortConfig = { sortConfig.value },
            onUpdateSortConfig = { vm.intent(AlbumsAction.UpdateSortConfig(it)) },
            onSelectSortAction = { vm.intent(AlbumsAction.SelectSortAction(it)) }
        )

        CancellableInputerBarPanel(
            isVisible = { state.showSearcherPanel },
            onDismiss = { vm.intent(AlbumsAction.HideSearcherPanel) },
            keyword = { state.searchKeyWord },
            onUpdateKeyword = { vm.intent(AlbumsAction.SearchFor(it)) }
        )

        AlbumsScreenContent(
            albums = albums,
            showText = { state.showText },
            onClickAlbum = { album, sharedMap ->
                val coverCacheKey = context.retrieveCacheKey(album)

                AppRouter.route("/pages/albums/detail")
                    .with("albumId", album.id)
                    .with("album", album)
                    .with("sharedMap", sharedMap)
                    .with("coverCacheKey", coverCacheKey)
                    .push()
            }
        )
    }
}
