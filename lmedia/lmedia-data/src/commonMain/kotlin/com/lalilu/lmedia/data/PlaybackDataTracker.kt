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

package com.lalilu.lmedia.data

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.LHistory
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
@Single
class PlaybackDataTracker(
    private val historyRepository: HistoryRepository
) {
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

    fun onMediaItemTransition(
        mediaId: String?,
        title: String?,
        isRepeating: Boolean = false,
        isNormalTransition: Boolean = true
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

    fun onIsPlayingChanged(isPlaying: Boolean) {
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
