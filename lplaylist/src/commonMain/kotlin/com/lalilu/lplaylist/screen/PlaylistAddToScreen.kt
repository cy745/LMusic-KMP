/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lalilu.lplaylist.screen


import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import com.lalilu.RemixIcon
import com.lalilu.extensions.GlobalToaster
import com.lalilu.extensions.ItemSelector
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.lplaylist.generated.resources.Res
import com.lalilu.lplaylist.lplaylist.generated.resources.playlist_action_add_to_playlist
import com.lalilu.lplaylist.repository.PlaylistRepository
import com.lalilu.navigation.*
import com.lalilu.remixicon.System
import com.lalilu.remixicon.system.checkLine
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.jvm.Transient

@Destination("/playlist/add")
data class PlaylistAddToScreen(
    private val mediaIds: List<String>,
) : Screen, ScreenInfoFactory, ScreenActionFactory {
    override val key: String = "${super.key}:${mediaIds.hashCode()}"

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember(this) {
        ScreenInfo(title = { stringResource(Res.string.playlist_action_add_to_playlist) })
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val playlistRepo: PlaylistRepository = koinInject()
        val scope = rememberCoroutineScope()

        return remember {
            listOf(
                ScreenAction.Static(
                    title = { stringResource(Res.string.playlist_action_add_to_playlist) },
                    icon = { RemixIcon.System.checkLine },
                    color = { Color(0xFF008521) },
                    onAction = { context ->
                        context.onDismiss()

                        val playlistIds = selector.selected()
                            .map { it.id }
                        selector.clear()

                        scope.launch {
                            playlistRepo.addMediaIdsToPlaylists(
                                mediaIds = mediaIds,
                                playlistIds = playlistIds
                            )
                            GlobalToaster?.show("已将媒体添加到歌单成功")
                        }
                    }
                )
            )
        }
    }

    @Transient
    private var selector = ItemSelector<LPlaylist>()

    @Composable
    override fun Content() {
        val playlistRepo = koinInject<PlaylistRepository>()
        val selector = remember { ItemSelector<LPlaylist>() }
            .also { this.selector = it }
        val playlists = remember { playlistRepo.getPlaylistsFlow() }
            .collectAsState(emptyList())

        PlaylistAddToScreenContent(
            mediaIds = mediaIds,
            selector = selector,
            playlists = { playlists.value }
        )
    }
}