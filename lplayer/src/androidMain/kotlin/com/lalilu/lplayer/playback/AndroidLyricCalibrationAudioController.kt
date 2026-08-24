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

import com.lalilu.llyricview.calibration.LyricCalibrationAudioConfig
import com.lalilu.llyricview.calibration.LyricCalibrationAudioController
import com.lalilu.llyricview.calibration.LyricCalibrationAudioState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * 将歌词校准协议组合到 Android 旁路播放器。
 *
 * 校准开始前只暂停主播放器，不触碰歌曲队列；校准自然结束、主动停止或异常时，仅在主
 * 播放器原先处于播放状态的情况下恢复播放。
 */
@Single(binds = [LyricCalibrationAudioController::class])
class AndroidLyricCalibrationAudioController(
    private val bypassAudioPlayer: BypassAudioPlayer,
    private val playback: Playback,
) : LyricCalibrationAudioController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LyricCalibrationAudioState>(LyricCalibrationAudioState.Idle)

    override val state: StateFlow<LyricCalibrationAudioState> = _state.asStateFlow()

    private var sessionId = 0L
    private var sessionActive = false
    private var resumeMainPlayer = false
    private var completionJob: Job? = null
    private val trackCache = mutableMapOf<LyricCalibrationAudioConfig, Pcm16AudioClip>()

    override suspend fun start(config: LyricCalibrationAudioConfig) {
        mutex.withLock {
            val replacingActiveSession = sessionActive
            sessionId += 1L
            completionJob?.cancel()
            bypassAudioPlayer.stop()

            if (!replacingActiveSession) {
                resumeMainPlayer = playback.isPlaying.value
                if (resumeMainPlayer) playback.pause()
            }
            sessionActive = true

            try {
                val clip = trackCache[config] ?: withContext(Dispatchers.Default) {
                    CalibrationDrumTrackGenerator.generate(config)
                }.also { trackCache[config] = it }
                bypassAudioPlayer.play(clip)
                _state.value = LyricCalibrationAudioState.Playing(config.durationMs)
                monitorCompletion(sessionId)
            } catch (throwable: Throwable) {
                finishSessionLocked(
                    failedMessage = throwable.message ?: "无法播放校准音频",
                    stopBypass = true,
                )
            }
        }
    }

    override fun stop() {
        scope.launch {
            mutex.withLock {
                sessionId += 1L
                completionJob?.cancel()
                finishSessionLocked(stopBypass = true)
            }
        }
    }

    override fun currentPositionMs(): Long = bypassAudioPlayer.currentPositionMs()

    private fun monitorCompletion(expectedSessionId: Long) {
        completionJob = scope.launch {
            val terminalState = bypassAudioPlayer.state.first {
                it !is BypassAudioPlayerState.Playing
            }
            mutex.withLock {
                if (!sessionActive || sessionId != expectedSessionId) return@withLock
                finishSessionLocked(
                    failedMessage = (terminalState as? BypassAudioPlayerState.Failed)
                        ?.cause?.message,
                    stopBypass = false,
                )
            }
        }
    }

    private suspend fun finishSessionLocked(
        failedMessage: String? = null,
        stopBypass: Boolean,
    ) {
        if (stopBypass) bypassAudioPlayer.stop()
        val shouldResume = sessionActive && resumeMainPlayer
        sessionActive = false
        resumeMainPlayer = false
        _state.value = failedMessage?.let { LyricCalibrationAudioState.Failed(it) }
            ?: LyricCalibrationAudioState.Idle
        if (shouldResume) playback.play()
    }
}
