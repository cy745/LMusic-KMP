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

package com.lalilu.lhistory

import com.lalilu.common.ext.io
import com.lalilu.lhistory.entity.LHistory
import com.lalilu.lhistory.repository.HistoryRepository
import com.lalilu.lplayer.playback.IPlaybackDataTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * 播放数据追踪器 - 统一单例实现
 *
 * 追踪播放状态并将播放数据存储到数据库
 */
@OptIn(ExperimentalTime::class)
@Single(binds = [IPlaybackDataTracker::class])
class PlaybackDataTracker(
    private val historyRepository: HistoryRepository
) : IPlaybackDataTracker {
    private val scope = CoroutineScope(Dispatchers.io) + SupervisorJob()
    private var playingItem: PlayingItemHandler? = null
    private var loopJob: Job? = null

    init {
        startLoopUpdate()
    }

    private fun startLoopUpdate() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                delay(5000L)
                saveOldPlayingItem(force = true)
            }
        }
    }

    override fun onMediaItemTransition(
        mediaId: String?,
        title: String?,
        isRepeating: Boolean,
        isNormalTransition: Boolean
    ) {
        mediaId ?: return

        when {
            playingItem == null -> {
                setNewPlayingItem(
                    mediaId = mediaId,
                    title = title.toString()
                )
            }

            playingItem?.mediaId != mediaId -> {
                saveOldPlayingItem()
                setNewPlayingItem(
                    mediaId = mediaId,
                    title = title.toString(),
                    isPlaying = isNormalTransition
                )
            }

            isRepeating -> {
                playingItem?.updateRepeatCount(1)
                saveOldPlayingItem()
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (playingItem == null) {
            return
        }

        playingItem?.updateIsPlaying(isPlaying)
        if (!isPlaying) {
            saveOldPlayingItem()
        }
    }

    private fun setNewPlayingItem(
        mediaId: String,
        title: String,
        isPlaying: Boolean = false
    ) = scope.launch(Dispatchers.Main.immediate) {
        val startTime = Clock.System.now().toEpochMilliseconds()
        val unUsedHistory = historyRepository.getUnUsedPreSaveHistory(mediaId)

        val primaryKey = unUsedHistory?.id ?: historyRepository.preSaveHistory(
            LHistory(
                contentId = mediaId,
                contentTitle = title,
                startTime = startTime,
                duration = -1,
            )
        )

        playingItem = PlayingItemHandler(
            primaryKey = primaryKey,
            mediaId = mediaId,
            startTime = startTime
        ).apply {
            updateIsPlaying(isPlaying)
        }
    }

    private fun saveOldPlayingItem(force: Boolean = false) {
        val item = playingItem ?: return

        if (force) {
            item.tryUpdateDuration()
        } else {
            if (item.isPlaying) {
                item.updateIsPlaying(false)
            }
        }

        scope.launch {
            historyRepository.updateHistory(
                id = item.primaryKey,
                duration = item.duration,
                repeatCount = item.repeatCount,
                startTime = item.startTime
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
private class PlayingItemHandler(
    val primaryKey: Long,
    val mediaId: String,
    val startTime: Long = Clock.System.now().toEpochMilliseconds(),
) {
    var lastPlayTime = startTime
        private set
    var isPlaying: Boolean = false
        private set
    var duration: Long = 0
        private set
    var repeatCount: Int = 0
        private set

    fun updateRepeatCount(repeatCount: Int) {
        this.repeatCount += repeatCount
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        if (isPlaying) {
            lastPlayTime = Clock.System.now().toEpochMilliseconds()
        } else {
            duration += Clock.System.now().toEpochMilliseconds() - lastPlayTime
        }
        this.isPlaying = isPlaying
    }

    fun tryUpdateDuration() {
        if (!isPlaying) return

        val now = Clock.System.now().toEpochMilliseconds()
        duration += now - lastPlayTime
        lastPlayTime = now
    }
}
