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

package com.lalilu.lmusic.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lalilu.RemixIcon
import com.lalilu.animated
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.remixicon.Media
import com.lalilu.remixicon.media.skipForwardLine

@Composable
fun PlayingInfoCard(
    modifier: Modifier = Modifier,
    currentPlaying: () -> LAudio? = { null },
    currentProgress: () -> Float = { 0f },
    isPlaying: () -> Boolean = { false },
    hasNext: () -> Boolean = { false },
    onClickPlayPause: () -> Unit = {},
    onClickNext: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 240.dp),
        interactionSource = interactionSource,
        color = MaterialTheme.colorScheme.onBackground.copy(0.05f),
        onClick = onClick
    ) {
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { slideInVertically { -it } togetherWith slideOutVertically { it } },
            targetState = currentPlaying(),
            label = ""
        ) { playing ->
            val progress = remember(playing) { mutableStateOf(0f) }
            val bgColor = MaterialTheme.colorScheme.primaryContainer
            val progressValue = remember {
                derivedStateOf {
                    if (currentPlaying() == playing) {
                        currentProgress().also { progress.value = it }
                    } else {
                        progress.value
                    }
                }
            }.animated()

            Row(
                modifier = Modifier.fillMaxSize()
                    .drawBehind {
                        drawRect(
                            color = bgColor,
                            size = size.copy(width = size.width * progressValue.value)
                        )
                    }
                    .padding(vertical = 8.dp)
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                    model = playing,
                    contentScale = ContentScale.Crop,
                    contentDescription = null
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(30.dp)
                        ),
                        text = playing?.title ?: "Unknown",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(30.dp)
                        ),
                        text = playing?.subtitle ?: "Unknown",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    enabled = hasNext(),
                    onClick = onClickNext
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = RemixIcon.Media.skipForwardLine,
                        tint = MaterialTheme.colorScheme.onBackground,
                        contentDescription = null
                    )
                }
            }
        }
    }
}