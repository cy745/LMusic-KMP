package com.lalilu.lplaylist.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.lalilu.RemixIcon
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lplaylist.viewmodel.PlaylistsAction
import com.lalilu.lplaylist.viewmodel.PlaylistsVM
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.remixicon.Media
import com.lalilu.remixicon.media.playListFill
import org.koin.compose.viewmodel.koinViewModel


@Destination("/pages/playlist")
object PlaylistScreen : Screen, ScreenInfoFactory {
    override fun isTabScreen(): Boolean = true

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "Playlist" },
            icon = RemixIcon.Media.playListFill
        )
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<PlaylistsVM>()
        val state by vm.state

        PlaylistScreenContent(
            isSearching = { state.searchKeyWord.isNotBlank() && !state.showSearcherPanel },
            onStartSearch = { vm.intent(PlaylistsAction.ShowSearcherPanel) },
            isSelected = { vm.selector.isSelected(it) },
            isSelecting = { vm.selector.isSelecting.value },
            playlists = { vm.playlists.value },
            onUpdatePlaylist = { vm.intent(PlaylistsAction.UpdatePlaylist(it)) },
            onLongClickPlaylist = { vm.selector.onSelect(it) },
            onClickPlaylist = {
                if (vm.selector.isSelecting.value) {
                    vm.selector.onSelect(it)
                } else {
                    AppRouter.route("/pages/playlist/detail")
                        .with("playlistId", it.id)
                        .push()
                }
            }
        )
    }
}