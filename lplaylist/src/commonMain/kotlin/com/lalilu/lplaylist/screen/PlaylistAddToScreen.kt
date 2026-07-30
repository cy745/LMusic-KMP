/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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