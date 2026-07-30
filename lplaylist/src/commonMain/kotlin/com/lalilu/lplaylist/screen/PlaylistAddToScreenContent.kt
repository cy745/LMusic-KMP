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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.extensions.ItemSelector
import com.lalilu.lplaylist.component.PlaylistCard
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.lplaylist.generated.resources.Res
import com.lalilu.lplaylist.lplaylist.generated.resources.playlist_action_add_to_playlist
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.NavIntent
import com.lalilu.navigation.smartbar.NavigatorHeader
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource


@Composable
internal fun PlaylistAddToScreenContent(
    mediaIds: List<String>,
    selector: ItemSelector<LPlaylist>,
    playlists: () -> List<LPlaylist>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            NavigatorHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                title = stringResource(Res.string.playlist_action_add_to_playlist),
                subTitle = "[S: ${mediaIds.size}] -> [P: ${selector.selected().size}]"
            ) {
                IconButton(
                    onClick = {
                        AppRouter.intent(
                            NavIntent.Push(PlaylistEditScreen())
                        )
                    }
                ) {
                    Icon(
                        imageVector = vectorResource(RemixIcon.System.addLine),
                        contentDescription = null
                    )
                }
            }
        }

        items(
            items = playlists(),
            key = { it.id },
            contentType = { LPlaylist::class }
        ) { playlist ->
            PlaylistCard(
                playlist = playlist,
                isSelected = { selector.isSelected(playlist) },
                onClick = { selector.onSelect(playlist) }
            )
        }
    }
}