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

package com.lalilu.lplaylist


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.RemixIcon
import com.lalilu.common.ext.io
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.GlobalToaster
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplaylist.repository.PlaylistRepository
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.ScreenAction
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

@Factory(binds = [ScreenAction::class])
@Named("add_to_playlist_action")
fun provideAddToPlaylistAction(
    selectedItems: () -> Collection<LAudio>
): ScreenAction.Static = ScreenAction.Static(
    title = { "添加到歌单" },
    icon = { RemixIcon.Media.playListAddLine },
    color = { Color(0xFF24A800) },
    onAction = { context ->
        context.onDismiss()
        val items = selectedItems()

        AppRouter.route("/playlist/add")
            .with("mediaIds", items.map { it.id })
            .jump()
    }
)

@OptIn(DelicateCoroutinesApi::class)
@Factory(binds = [ScreenAction::class])
@Named("add_to_favourite_action")
fun provideAddToFavouriteAction(
    selectedItems: () -> Collection<LAudio>
): ScreenAction.Static = ScreenAction.Static(
    title = { "添加到我喜欢" },
    icon = { RemixIcon.HealthAndMedical.heart3Line },
    color = { MaterialTheme.colorScheme.primary },
    onAction = { context ->
        val items = selectedItems().map { it.id }
        if (items.isEmpty()) {
            context.onDismiss()
            GlobalToaster?.show("请先选中歌曲")
            return@Static
        }

        val playlistRepo = requestFor<PlaylistRepository>()
        if (playlistRepo == null) {
            context.onDismiss()
            GlobalToaster?.show("播放列表未初始化")
            return@Static
        }

        GlobalScope.launch(Dispatchers.io) {
            context.onDismiss()
            playlistRepo.addMediaIdsToFavourite(items)
            GlobalToaster?.show("已添加${items.size}首歌曲至我喜欢")
        }
    }
)

@Factory(binds = [ScreenAction::class])
@Named("like_action")
fun provideLikeAction(
    mediaId: String,
    playlistRepo: PlaylistRepository,
) = ScreenAction.Dynamic { actionContext ->
    val isLiked by playlistRepo.isItemInFavourite(mediaId)
        .collectAsState(initial = false)

    val scope = rememberCoroutineScope()
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pressedState = interactionSource.collectIsPressedAsState()
    val iconColor by animateColorAsState(
        targetValue = if (isLiked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onBackground.copy(0.3f),
        label = ""
    )
    val scaleValue by animateFloatAsState(
        animationSpec = SpringSpec(dampingRatio = Spring.DampingRatioMediumBouncy),
        targetValue = if (pressedState.value) 1.2f else 1f,
        label = ""
    )

    Surface(
        modifier = Modifier,
        color = iconColor.copy(0.15f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .toggleable(
                    value = isLiked,
                    onValueChange = { like ->
                        scope.launch {
                            if (like) playlistRepo.addMediaIdsToFavourite(mediaIds = listOf(mediaId))
                            else playlistRepo.removeMediaIdsFromFavourite(mediaIds = listOf(mediaId))
                        }
                        if (like) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    role = Role.Checkbox,
                    interactionSource = interactionSource,
                    indication = null
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .scale(scaleValue),
                imageVector = if (isLiked) vectorResource(RemixIcon.HealthAndMedical.heart3Fill)
                else vectorResource(RemixIcon.HealthAndMedical.heart3Line),
                tint = iconColor,
                contentDescription = "A Checkable Button"
            )

            if (actionContext.isFullyExpanded) {
                Text(
                    text = "收藏",
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    color = iconColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}