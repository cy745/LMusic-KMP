package com.lalilu.lplayer.playback

import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Abstract base implementation of Playback interface
 * Provides common functionality for all platform implementations
 */
@Suppress("PropertyName")
abstract class AbstractPlayback(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.io + SupervisorJob())
) : Playback, CoroutineScope by coroutineScope {

    // Protected mutable state flows
    protected val _isPlaying = MutableStateFlow(false)
    protected val _errors = MutableSharedFlow<Throwable>()
    protected val _currentDuration = MutableStateFlow(0L)
    protected val _currentBufferedPosition = MutableStateFlow(0L)
    protected val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    protected var _pauseWhenCompletion: Boolean = false
    protected var _shuffledIndices: List<Int> = emptyList()
    protected var _currentIndexInShuffled: Int = 0

    // Public state flows
    override val queue: PlayableQueue = PlayableQueueImpl()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val errors: SharedFlow<Throwable> = _errors.asSharedFlow()
    override val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    override val currentBufferedPosition: StateFlow<Long> = _currentBufferedPosition.asStateFlow()
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    /**
     * 当播放完成时调用
     */
    protected suspend fun onCompletion() {
        if (_pauseWhenCompletion) pause() else skipToNext()
    }

    // Default implementations
    override suspend fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    override suspend fun skipToNext() {
        val currentState = queue.stateSnapshot()
        val flattened = currentState.list
        if (flattened.isEmpty()) return

        val nextIndex = when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP -> currentState.index
            PlaybackMode.SHUFFLE -> {
                if (_shuffledIndices.isEmpty()) {
                    updateShuffledIndices()
                }
                _currentIndexInShuffled = (_currentIndexInShuffled + 1) % _shuffledIndices.size
                _shuffledIndices[_currentIndexInShuffled]
            }

            PlaybackMode.LOOP -> (currentState.index + 1) % flattened.size
            PlaybackMode.SEQUENTIAL -> {
                if (currentState.index < flattened.size - 1) {
                    currentState.index + 1
                } else {
                    -1 // End of playlist
                }
            }
        }

        if (nextIndex != -1) {
            skipTo(index = nextIndex, start = true)
        }
    }

    override suspend fun skipToPrevious() {
        val currentState = queue.stateSnapshot()
        val flattened = currentState.list
        if (flattened.isEmpty()) return

        val previousIndex = when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP -> currentState.index
            PlaybackMode.SHUFFLE -> {
                if (_shuffledIndices.isEmpty()) {
                    updateShuffledIndices()
                }
                _currentIndexInShuffled = (_currentIndexInShuffled - 1 + _shuffledIndices.size) % _shuffledIndices.size
                _shuffledIndices[_currentIndexInShuffled]
            }

            PlaybackMode.LOOP -> (currentState.index - 1 + flattened.size) % flattened.size
            PlaybackMode.SEQUENTIAL -> {
                if (currentState.index > 0) {
                    currentState.index - 1
                } else {
                    -1 // Beginning of playlist
                }
            }
        }

        if (previousIndex != -1) {
            skipTo(index = previousIndex, true)
        }
    }

    override suspend fun updatePlaylist(playlist: List<LItem>, startIndex: Int, start: Boolean) {
        queue.replaceAll(items = playlist, index = startIndex)
        if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            updateShuffledIndices()
        }
        skipTo(startIndex, start)
    }

    override suspend fun clearPlaylist() {
        queue.clear()
        _shuffledIndices = emptyList()
        _currentIndexInShuffled = 0
    }

    override suspend fun setPlaybackMode(mode: PlaybackMode) {
        if (_playbackMode.value == mode) return

        val oldMode = _playbackMode.value
        _playbackMode.value = mode

        // When switching to or from shuffle mode, we need to update the indices
        if (oldMode == PlaybackMode.SHUFFLE || mode == PlaybackMode.SHUFFLE) {
            if (mode == PlaybackMode.SHUFFLE) {
                updateShuffledIndices()
                val currentIndex = queue.stateSnapshot().index

                // Update current index in shuffled list
                _currentIndexInShuffled = _shuffledIndices.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
            } else {
                // When leaving shuffle mode, we might want to adjust the current index
                // to match the original playlist order
                if (oldMode == PlaybackMode.SHUFFLE) {
                    queue.switchTo(_shuffledIndices.getOrNull(_currentIndexInShuffled) ?: 0)
                }
            }
        }
    }

    override suspend fun setPauseWhenCompletion(cancel: Boolean) {
        _pauseWhenCompletion = !cancel
    }

    private fun updateShuffledIndices() {
        val currentState = queue.stateSnapshot()
        val size = currentState.list.size
        _shuffledIndices = (0 until size).toList().shuffled(Random.Default)
        _currentIndexInShuffled = _shuffledIndices.indexOf(currentState.index).takeIf { it >= 0 } ?: 0
    }

    protected fun emitError(error: Throwable) {
        launch { _errors.emit(error) }
    }
}