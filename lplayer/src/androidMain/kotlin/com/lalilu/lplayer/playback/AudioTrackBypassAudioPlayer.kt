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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val NanosPerSecond = 1_000_000_000L
private const val MaxTimestampDriftNanos = 5L * NanosPerSecond
private const val MaxPlaybackHeadInterpolationMs = 40L

/** Android 端基于流式 [AudioTrack] 的 PCM 旁路播放器。 */
@RequiresApi(Build.VERSION_CODES.M)
@Single(binds = [BypassAudioPlayer::class])
class AudioTrackBypassAudioPlayer : BypassAudioPlayer {
    private val lock = Any()
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<BypassAudioPlayerState>(BypassAudioPlayerState.Idle)

    override val state: StateFlow<BypassAudioPlayerState> = _state.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var sampleRateHz: Int = 0
    private var clipFrameCount: Long = 0L
    private var writerJob: Job? = null
    private val positionClock = AudioTrackPositionClock()
    private var requestGeneration = 0L

    override suspend fun play(clip: Pcm16AudioClip) {
        val generation = synchronized(lock) {
            requestGeneration += 1L
            releaseLocked()
            _state.value = BypassAudioPlayerState.Idle
            requestGeneration
        }

        val track = try {
            buildAudioTrack(clip)
        } catch (throwable: Throwable) {
            failRequest(generation, throwable)
            throw throwable
        }

        try {
            val prefillSampleCount = min(
                clip.samples.size,
                track.bufferSizeInFrames * clip.channelCount,
            )
            val prefilled = withContext(Dispatchers.IO) {
                writeFully(
                    track = track,
                    samples = clip.samples,
                    startIndex = 0,
                    sampleCount = prefillSampleCount,
                )
            }

            track.setNotificationMarkerPosition(clip.frameCount)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(completedTrack: AudioTrack) {
                        synchronized(lock) {
                            if (audioTrack === completedTrack) {
                                releaseLocked()
                                _state.value = BypassAudioPlayerState.Idle
                            }
                        }
                    }

                    override fun onPeriodicNotification(track: AudioTrack) = Unit
                },
                callbackHandler,
            )

            // MODE_STREAM 只分配一个很小的循环缓冲区，剩余 PCM 在后台按播放速度持续写入。
            // 这避免了部分 Android 音频栈无法为十余秒音轨分配 MODE_STATIC 共享内存的问题。
            val streamJob = writerScope.launch(start = CoroutineStart.LAZY) {
                try {
                    writeFully(
                        track = track,
                        samples = clip.samples,
                        startIndex = prefilled,
                        sampleCount = clip.samples.size - prefilled,
                    )

                    // 部分厂商实现不会稳定派发 marker 回调，以播放头作为完成兜底。
                    while (isActive && unsignedPlaybackHead(track) < clip.frameCount.toLong()) {
                        delay(10L)
                    }
                    completeIfCurrent(track)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    failIfCurrent(track, throwable)
                }
            }

            val accepted = synchronized(lock) {
                if (generation != requestGeneration) {
                    false
                } else {
                    audioTrack = track
                    sampleRateHz = clip.sampleRateHz
                    clipFrameCount = clip.frameCount.toLong()
                    positionClock.reset()
                    writerJob = streamJob
                    _state.value = BypassAudioPlayerState.Playing(clip.durationMs)
                    track.play()
                    true
                }
            }
            if (!accepted) {
                streamJob.cancel()
                runCatching { track.release() }
                return
            }
            streamJob.start()
        } catch (throwable: Throwable) {
            synchronized(lock) {
                if (generation == requestGeneration) {
                    if (audioTrack === track) {
                        releaseLocked()
                    } else {
                        runCatching { track.release() }
                    }
                    _state.value = BypassAudioPlayerState.Failed(throwable)
                } else if (audioTrack !== track) {
                    runCatching { track.release() }
                }
            }
            throw throwable
        }
    }

    override fun stop() {
        synchronized(lock) {
            requestGeneration += 1L
            releaseLocked()
            _state.value = BypassAudioPlayerState.Idle
        }
    }

    override fun currentPositionMs(): Long = synchronized(lock) {
        val track = audioTrack ?: return@synchronized 0L
        val sampleRate = sampleRateHz.takeIf { it > 0 } ?: return@synchronized 0L
        val frames = positionClock.currentPositionFrames(
            track = track,
            sampleRateHz = sampleRate,
            maxFrameCount = clipFrameCount,
        )
        frames * 1_000L / sampleRate
    }

    private fun buildAudioTrack(clip: Pcm16AudioClip): AudioTrack {
        val channelMask = when (clip.channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBufferSizeBytes = AudioTrack.getMinBufferSize(
            clip.sampleRateHz,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferSizeBytes > 0) {
            "AudioTrack does not support ${clip.sampleRateHz}Hz/${clip.channelCount}ch PCM " +
                    "(minBuffer=$minBufferSizeBytes)"
        }
        val targetBufferSizeBytes = clip.sampleRateHz * clip.channelCount * Short.SIZE_BYTES / 5
        val bufferSizeBytes = maxOf(minBufferSizeBytes, targetBufferSizeBytes)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            // 与主播放器保持一致，避免系统将校准音作为提示音送入另一套处理或缓冲链路。
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSpatializationBehavior(AudioAttributes.SPATIALIZATION_BEHAVIOR_AUTO)
                }
            }
            .build()

        return AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(clip.sampleRateHz)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSizeBytes)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()
            .also { track ->
                check(track.state == AudioTrack.STATE_INITIALIZED) {
                    "AudioTrack init failed: ${clip.sampleRateHz}Hz/${clip.channelCount}ch, " +
                            "streamBuffer=$bufferSizeBytes bytes, minBuffer=$minBufferSizeBytes bytes"
                }
            }
    }

    private fun releaseLocked() {
        val track = audioTrack ?: return
        audioTrack = null
        sampleRateHz = 0
        clipFrameCount = 0L
        positionClock.reset()
        writerJob?.cancel()
        writerJob = null
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.release() }
    }

    private fun writeFully(
        track: AudioTrack,
        samples: ShortArray,
        startIndex: Int,
        sampleCount: Int,
    ): Int {
        var offset = startIndex
        val end = startIndex + sampleCount
        while (offset < end) {
            val written = track.write(
                samples,
                offset,
                end - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            check(written > 0) { "AudioTrack.write failed with code $written" }
            offset += written
        }
        return offset
    }

    private fun unsignedPlaybackHead(track: AudioTrack): Long =
        track.playbackHeadPosition.toLong() and 0xffff_ffffL

    private fun completeIfCurrent(track: AudioTrack) {
        synchronized(lock) {
            if (audioTrack === track) {
                releaseLocked()
                _state.value = BypassAudioPlayerState.Idle
            }
        }
    }

    private fun failIfCurrent(track: AudioTrack, throwable: Throwable) {
        synchronized(lock) {
            if (audioTrack === track) {
                releaseLocked()
                _state.value = BypassAudioPlayerState.Failed(throwable)
            }
        }
    }

    private fun failRequest(generation: Long, throwable: Throwable) {
        synchronized(lock) {
            if (generation == requestGeneration) {
                _state.value = BypassAudioPlayerState.Failed(throwable)
            }
        }
    }
}

/**
 * 将 [AudioTrack] 的输出位置转换为连续的帧时钟。
 *
 * 逻辑与 Media3 的位置追踪原则保持一致：优先使用 AudioTimestamp 给出的硬件呈现位置，
 * 并通过其单调系统时间在两次采样之间外推；时间戳尚未开始推进或设备不支持时，再使用
 * playbackHeadPosition。后者粒度通常约为 20ms，因此只允许短距离插值，避免欠载或暂停时
 * 时钟脱离真实播放头继续前进。
 */
private class AudioTrackPositionClock {
    private val timestamp = AudioTimestamp()
    private var previousTimestampFrame: Long? = null
    private var timestampHasAdvanced = false
    private var rawAnchorFrame = 0L
    private var rawAnchorNanos = 0L
    private var lastReportedFrame = 0L

    fun currentPositionFrames(
        track: AudioTrack,
        sampleRateHz: Int,
        maxFrameCount: Long,
    ): Long {
        val nowNanos = System.nanoTime()
        val timestampPosition = readTimestampPosition(
            track = track,
            sampleRateHz = sampleRateHz,
            nowNanos = nowNanos,
        )
        val playbackHeadPosition = estimatePlaybackHeadPosition(
            track = track,
            sampleRateHz = sampleRateHz,
            nowNanos = nowNanos,
        )
        val position = (timestampPosition ?: playbackHeadPosition)
            .coerceIn(0L, maxFrameCount)

        // AudioTrack 在切换时间戳来源时可能出现很小的回跳；校准音轨只会向前播放，
        // 因此对外保持单调能避免一次点击跨到错误的节拍区间。
        lastReportedFrame = max(lastReportedFrame, position)
        return lastReportedFrame
    }

    fun reset() {
        previousTimestampFrame = null
        timestampHasAdvanced = false
        rawAnchorFrame = 0L
        rawAnchorNanos = 0L
        lastReportedFrame = 0L
    }

    private fun readTimestampPosition(
        track: AudioTrack,
        sampleRateHz: Int,
        nowNanos: Long,
    ): Long? {
        if (!track.getTimestamp(timestamp)) return null
        if (timestamp.framePosition < 0L) return null
        if (abs(nowNanos - timestamp.nanoTime) > MaxTimestampDriftNanos) return null

        val previousFrame = previousTimestampFrame
        if (previousFrame != null && timestamp.framePosition > previousFrame) {
            timestampHasAdvanced = true
        }
        previousTimestampFrame = max(previousFrame ?: timestamp.framePosition, timestamp.framePosition)
        if (!timestampHasAdvanced) return null

        val elapsedNanos = (nowNanos - timestamp.nanoTime).coerceAtLeast(0L)
        return timestamp.framePosition + elapsedNanos * sampleRateHz / NanosPerSecond
    }

    private fun estimatePlaybackHeadPosition(
        track: AudioTrack,
        sampleRateHz: Int,
        nowNanos: Long,
    ): Long {
        // playbackHeadPosition 是无符号 32-bit 帧计数；校准音很短不会回绕，但仍按无符号读取。
        val rawFrame = track.playbackHeadPosition.toLong() and 0xffff_ffffL
        if (rawAnchorNanos == 0L || rawFrame > rawAnchorFrame) {
            rawAnchorFrame = rawFrame
            rawAnchorNanos = nowNanos
        }
        if (rawFrame == 0L || track.playState != AudioTrack.PLAYSTATE_PLAYING) return rawFrame

        val extrapolatedFrame = rawAnchorFrame +
                (nowNanos - rawAnchorNanos).coerceAtLeast(0L) * sampleRateHz / NanosPerSecond
        val maxInterpolationFrames = sampleRateHz * MaxPlaybackHeadInterpolationMs / 1_000L
        return min(extrapolatedFrame, rawFrame + maxInterpolationFrames)
    }
}
