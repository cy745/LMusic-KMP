package com.lalilu.lartist.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import coil3.compose.LocalPlatformContext
import com.lalilu.RemixIcon
import com.lalilu.extensions.retrieveCacheKey
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lartist.lartist.generated.resources.Res
import com.lalilu.lartist.lartist.generated.resources.artist_screen_title
import com.lalilu.lartist.viewmodel.ArtistsAction
import com.lalilu.lartist.viewmodel.ArtistsVM
import com.lalilu.lmedia.dialog.SortPanelDialog
import com.lalilu.navigation.*
import com.lalilu.navigation.smartbar.CancellableInputerBarPanel
import com.lalilu.remixicon.Editor
import com.lalilu.remixicon.Media
import com.lalilu.remixicon.System
import com.lalilu.remixicon.editor.sortDesc
import com.lalilu.remixicon.media.albumFill
import com.lalilu.remixicon.system.search2Line
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Destination("/pages/artists")
data object ArtistsScreen : Screen, ScreenInfoFactory, ScreenActionFactory, ScreenBarFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { stringResource(Res.string.artist_screen_title) },
            icon = RemixIcon.Media.albumFill
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = koinViewModel<ArtistsVM>()
        val state by vm.state

        return remember {
            listOf(
                ScreenAction.Static(
                    title = { "排序" },
                    icon = { RemixIcon.Editor.sortDesc },
                    color = { Color(0xFF1793FF) },
                    onAction = { vm.intent(ArtistsAction.ToggleSortPanel) }
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
                    onAction = { vm.intent(ArtistsAction.ToggleSearcherPanel) }
                ),
            )
        }
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<ArtistsVM>()

        val state by vm.state
        val artists by vm.artists
        val sortAction = vm.sorter.selectedAction.collectAsState()
        val sortConfig = vm.sorter.sortConfig.collectAsState()
        val context = LocalPlatformContext.current

        SortPanelDialog(
            isVisible = { state.showSortPanel },
            onDismiss = { vm.intent(ArtistsAction.HideSortPanel) },
            supportSortActions = vm.sorter.supportedActions,
            selectedSortAction = { sortAction.value },
            sortConfig = { sortConfig.value },
            onUpdateSortConfig = { vm.intent(ArtistsAction.UpdateSortConfig(it)) },
            onSelectSortAction = { vm.intent(ArtistsAction.SelectSortAction(it)) }
        )

        CancellableInputerBarPanel(
            isVisible = { state.showSearcherPanel },
            onDismiss = { vm.intent(ArtistsAction.HideSearcherPanel) },
            keyword = { state.searchKeyWord },
            onUpdateKeyword = { vm.intent(ArtistsAction.SearchFor(it)) }
        )

        ArtistsScreenContent(
            artists = artists,
            onClickArtist = { artist, sharedMap ->
                val coverCacheKey = context.retrieveCacheKey(artist)

                AppRouter.route("/pages/artists/detail")
                    .with("artistId", artist.id)
                    .with("artist", artist)
                    .with("sharedMap", sharedMap)
                    .with("coverCacheKey", coverCacheKey)
                    .push()
            }
        )
    }
}
