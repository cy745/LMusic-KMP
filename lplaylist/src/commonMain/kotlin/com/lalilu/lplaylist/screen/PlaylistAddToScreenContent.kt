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
import com.lalilu.remixicon.System
import com.lalilu.remixicon.system.addLine
import org.jetbrains.compose.resources.stringResource


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
                        imageVector = RemixIcon.System.addLine,
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