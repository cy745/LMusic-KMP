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

package com.lalilu.lplaylist.component


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.RemixIcon
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.repository.PlaylistRepository
import com.lalilu.remixicon.Editor
import com.lalilu.remixicon.HealthAndMedical
import com.lalilu.remixicon.editor.draggable
import com.lalilu.remixicon.healthandmedical.heart3Fill

@Composable
fun PlaylistCard(
    playlist: LPlaylist,
    modifier: Modifier = Modifier,
    draggingModifier: Modifier = Modifier,
    isDragging: () -> Boolean = { false },
    isSelected: () -> Boolean = { false },
    isSelecting: () -> Boolean = { false },
    onClick: (LPlaylist) -> Unit = {},
    onLongClick: (LPlaylist) -> Unit = {},
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isDragging() -> MaterialTheme.colorScheme.onBackground.copy(0.25f)
            isSelected() -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.onBackground.copy(0.05f)
        },
        label = ""
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .combinedClickable(
                onClick = { onClick(playlist) },
                onLongClick = { onLongClick(playlist) }
            )
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = playlist.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )

            if (playlist.subTitle.isNotBlank()) {
                Text(
                    text = playlist.subTitle,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.8f),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                )
            }
        }

        if (playlist.id == PlaylistRepository.FAVOURITE_PLAYLIST_ID) {
            Icon(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .scale(0.9f),
                imageVector = RemixIcon.HealthAndMedical.heart3Fill,
                tint = Color(0xFFFE4141),
                contentDescription = "heart_icon"
            )
        }

        Text(
            text = "${playlist.mediaIds.size}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        AnimatedVisibility(
            visible = isSelecting(),
            label = "DragHandleVisibility"
        ) {
            Icon(
                modifier = draggingModifier
                    .padding(start = 16.dp),
                imageVector = RemixIcon.Editor.draggable,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                contentDescription = "DragHandle",
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaylistCardPreview() {
    val playlist = LPlaylist(
        id = "12312312",
        title = "日语",
        subTitle = "日语相关的歌曲",
        coverUri = "",
        mediaIds = listOf("12312", "12312312")
    )
    PlaylistCard(
        playlist = playlist,
        isDragging = { false },
        onClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun PlaylistCardListPreview() {
    val playlist = LPlaylist(
        id = "12312312",
        title = "日语",
        subTitle = "日语相关的歌曲",
        coverUri = "",
        mediaIds = listOf("12312", "12312312")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PlaylistCard(
            playlist = playlist,
            isDragging = { false },
            onClick = {}
        )
        PlaylistCard(
            playlist = playlist,
            isDragging = { false },
            onClick = {}
        )
    }
}