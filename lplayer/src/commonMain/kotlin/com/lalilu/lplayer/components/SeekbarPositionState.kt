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

package com.lalilu.lplayer.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val PositionSettlingDurationMs = 220
private const val PlaybackPositionJumpThresholdMs = 250f

private data class PositionSource(
    val isDragging: Boolean,
    val position: Float,
)

/**
 * 统一管理进度条与歌词使用的交互时间。
 *
 * 正常播放时 [position] 直接等于播放器时间，不使用动画持续追踪，以免产生固定延迟；
 * 拖动时直接使用手势位置；离开拖动态后，只将交互位置与播放器位置之间的误差动画到
 * 0。内部 [Animatable] 因此只表示一个短暂的修正量，而不再保存绝对播放时间。
 */
@Stable
class SeekbarPositionState internal constructor(initialPosition: Float) {
    private var sourcePosition by mutableFloatStateOf(initialPosition)
    private val correction = Animatable(0f)
    private var wasDragging = false
    private var previousPlaybackPosition: Float? = null
    private var correctionJob: Job? = null
    private var correctionGeneration = 0
    private var isSettling = false

    val position: Float
        get() = sourcePosition + correction.value

    internal suspend fun track(
        isDragging: () -> Boolean,
        draggingPosition: () -> Float,
        playbackPosition: () -> Float,
    ) = coroutineScope {
        try {
            snapshotFlow {
                val dragging = isDragging()
                PositionSource(
                    isDragging = dragging,
                    position = if (dragging) draggingPosition() else playbackPosition(),
                )
            }
                .distinctUntilChanged()
                .collect { source ->
                    if (source.isDragging) {
                        updateDraggingPosition(source.position)
                    } else {
                        updatePlaybackPosition(source.position)
                    }
                    wasDragging = source.isDragging
                }
        } finally {
            stopTracking()
        }
    }

    private suspend fun updateDraggingPosition(position: Float) {
        cancelSettling()
        correction.snapTo(0f)
        sourcePosition = position
        previousPlaybackPosition = null
    }

    private suspend fun CoroutineScope.updatePlaybackPosition(position: Float) {
        val previousPosition = previousPlaybackPosition
        val positionJumped = previousPosition != null &&
                (position - previousPosition).absoluteValue > PlaybackPositionJumpThresholdMs

        if (wasDragging || (isSettling && positionJumped)) {
            settleTo(position)
        } else {
            sourcePosition = position
        }
        previousPlaybackPosition = position
    }

    private suspend fun CoroutineScope.settleTo(playbackPosition: Float) {
        val visiblePosition = position
        val generation = ++correctionGeneration
        correctionJob?.cancel()

        sourcePosition = playbackPosition
        correction.snapTo(visiblePosition - playbackPosition)
        isSettling = true
        correctionJob = launch {
            try {
                // correction 为 0 时也保留完整窗口，用来吸收异步 Seek 生效时可能出现的
                // 一次播放器位置跳变。
                coroutineScope {
                    launch {
                        correction.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = PositionSettlingDurationMs,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    delay(PositionSettlingDurationMs.toLong())
                }
            } finally {
                if (generation == correctionGeneration) {
                    isSettling = false
                }
            }
        }
    }

    private fun cancelSettling() {
        correctionGeneration += 1
        correctionJob?.cancel()
        correctionJob = null
        isSettling = false
    }

    private fun stopTracking() {
        cancelSettling()
        wasDragging = false
        previousPlaybackPosition = null
    }
}

@Composable
fun rememberSeekbarPositionState(
    initialPosition: Float = 0f,
): SeekbarPositionState = remember {
    SeekbarPositionState(initialPosition = initialPosition)
}
