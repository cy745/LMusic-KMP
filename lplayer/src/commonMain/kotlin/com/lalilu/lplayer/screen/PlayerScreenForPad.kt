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

package com.lalilu.lplayer.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.lalilu.adaptiveValue
import com.lalilu.animated
import com.lalilu.krouter.annotation.Destination
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricLayout
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.viewmodel.PlayerViewModel
import com.lalilu.navigation.Screen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@Destination("/player_pad")
class PlayerScreenForPad : Screen {

    @OptIn(ExperimentalTime::class)
    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val vm = koinViewModel<PlayerViewModel>()
        val currentPlaying = vm.currentItem.collectAsState(null)
        val currentTime = vm.currentTime
        val isPlaying = vm.isPlaying

        LifecycleResumeEffect(isPlaying.value) {
            val job = scope.launch {
                var lastTime = 0L
                while (isActive && isPlaying.value) {
                    withFrameMillis {
                        currentTime.value = LPlayer.instance.currentPosition()
                        val now = Clock.System.now().toEpochMilliseconds()
                        Logger.i("frametime: $it, ${now - lastTime}, currentTime: ${currentTime.value}")
                        lastTime = now
                    }
                }
            }
            onPauseOrDispose {
                job.cancel()
            }
        }

        PlayerScreenForPadContent(
            coverData = { currentPlaying.value },
            currentTime = { currentTime.value },
            lyricEntry = vm.lyricItems
        )
    }
}

@Composable
fun PlayerScreenForPadContent(
    modifier: Modifier = Modifier,
    coverData: () -> Any? = { null },
    currentTime: () -> Long = { 0L },
    lyricEntry: State<List<LyricItem>>,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Background(
            modifier = Modifier,
            coverData = coverData
        )

        val paddingHorizontal = adaptiveValue(
            compact = { 40.dp },
            medium = { 40.dp },
            expanded = { 80.dp }
        ).animated()

        Row(
            modifier = Modifier.fillMaxSize()
                .widthIn(max = 500.dp)
                .padding(horizontal = paddingHorizontal.value),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlayerPanel(
                modifier = Modifier.fillMaxHeight()
                    .weight(1f),
                coverData = coverData
            )

            LyricPanel(
                modifier = Modifier.fillMaxHeight()
                    .weight(1f),
                currentTime = currentTime,
                lyricEntry = lyricEntry
            )
        }
    }
}

@Composable
private fun PlayerPanel(
    modifier: Modifier = Modifier,
    coverData: () -> Any? = { null },

    ) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedContent(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            transitionSpec = {
                fadeIn(tween(500)) togetherWith fadeOut(tween(300, 500))
            },
            targetState = coverData(),
            label = ""
        ) { model ->
            Card(
                shape = RoundedCornerShape(2.dp)
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxWidth()
                        .aspectRatio(1f),
                    model = model,
                    contentScale = ContentScale.Crop,
                    contentDescription = null
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}


@Composable
fun LyricPanel(
    modifier: Modifier = Modifier,
    currentTime: () -> Long = { 0L },
    lyricEntry: State<List<LyricItem>>,
) {
    BoxWithConstraints(
        modifier = modifier
    ) {
        LyricLayout(
            modifier = Modifier.fillMaxSize(),
            currentTime = currentTime,
            screenConstraints = constraints,
            lyricEntry = lyricEntry,
            isUserClickEnable = { true },
            isUserScrollEnable = { false },
            onItemClick = { PlayerAction.SeekTo(it.time).action() },
            onItemLongClick = {}
        )
    }
}

@Composable
private fun Background(
    modifier: Modifier = Modifier,
    coverData: () -> Any? = { null }
) {
    val context = LocalPlatformContext.current

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(tween(500)) togetherWith fadeOut(tween(300, 500))
            },
            targetState = coverData(),
            label = ""
        ) { model ->
            AsyncImage(
                modifier = Modifier.fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.onBackground.copy(0.15f))
                    .blur(radius = 50.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                model = ImageRequest.Builder(context)
                    .data(model)
                    .size(400, 400)
                    .build(),
                contentScale = ContentScale.Crop,
                contentDescription = null,
                filterQuality = FilterQuality.None
            )
        }

        Spacer(
            modifier = Modifier.fillMaxSize()
                .background(color = Color.Black.copy(0.3f))
        )
    }
}