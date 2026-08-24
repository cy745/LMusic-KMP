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

package com.lalilu.lplayer.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * 可由旁路播放器直接播放的 16-bit PCM 短音频。
 *
 * 旁路音频不进入歌曲播放队列，也不会改变当前歌曲、进度或播放历史。PCM 数据由调用方
 * 一次性提供，适合节拍器、试听音和提示音等时长较短、需要精确播放头的场景。
 */
data class Pcm16AudioClip(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val channelCount: Int = 1,
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(channelCount == 1 || channelCount == 2) {
            "Only mono and stereo PCM are supported"
        }
        require(samples.size % channelCount == 0) {
            "PCM sample count must be divisible by channelCount"
        }
    }

    val frameCount: Int get() = samples.size / channelCount
    val durationMs: Long get() = frameCount * 1_000L / sampleRateHz
}

sealed interface BypassAudioPlayerState {
    data object Idle : BypassAudioPlayerState
    data class Playing(val durationMs: Long) : BypassAudioPlayerState
    data class Failed(val cause: Throwable) : BypassAudioPlayerState
}

/**
 * 不经过 [Playback] 歌曲队列的短音频播放器。
 *
 * 当前仅 Android 提供实现。一次只能播放一个音频；再次调用 [play] 会先释放旧音频。
 */
interface BypassAudioPlayer {
    val state: StateFlow<BypassAudioPlayerState>

    suspend fun play(clip: Pcm16AudioClip)

    fun stop()

    /** 返回旁路音轨的硬件呈现位置；时间戳不可用时才由播放头进行短距离插值。 */
    fun currentPositionMs(): Long
}
