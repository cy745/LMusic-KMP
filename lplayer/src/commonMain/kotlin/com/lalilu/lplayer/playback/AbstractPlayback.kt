package com.lalilu.lplayer.playback

import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.flatten
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
    protected val _playlist = MutableStateFlow<List<LItem>>(emptyList())
    protected val _currentItemIndex = MutableStateFlow(0)
    protected val _isPlaying = MutableStateFlow(false)
    protected val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    protected val _errors = MutableSharedFlow<Throwable>()
    protected val _currentDuration = MutableStateFlow(0L)
    protected val _currentBufferedPosition = MutableStateFlow(0L)
    protected val _canSeek = MutableStateFlow(false)
    protected val _canSkipNext = MutableStateFlow(false)
    protected val _canSkipPrevious = MutableStateFlow(false)
    protected val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    protected var shuffledIndices: List<Int> = emptyList()
    protected var currentIndexInShuffled: Int = 0
    protected var _pauseWhenCompletion: Boolean = false

    fun setPauseWhenCompletion(cancel: Boolean) {
        _pauseWhenCompletion = !cancel
    }

    // Public state flows
    override val playlist: StateFlow<List<LItem>> = _playlist.asStateFlow()
    override val currentItemIndex: StateFlow<Int> = _currentItemIndex.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    override val errors: SharedFlow<Throwable> = _errors.asSharedFlow()
    override val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    override val currentBufferedPosition: StateFlow<Long> = _currentBufferedPosition.asStateFlow()
    override val canSeek: StateFlow<Boolean> = _canSeek.asStateFlow()
    override val canSkipNext: StateFlow<Boolean> = _canSkipNext.asStateFlow()
    override val canSkipPrevious: StateFlow<Boolean> = _canSkipPrevious.asStateFlow()
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    protected val flattenedPlaylist: StateFlow<List<LAudio>> = _playlist
        .flatten<LAudio>()
        .stateIn(this, SharingStarted.WhileSubscribed(), emptyList())

    // Computed properties
    override val currentItem: StateFlow<LAudio?> = flattenedPlaylist
        .combine(_currentItemIndex) { playlist, index -> playlist.getOrNull(index) }
        .stateIn(this, SharingStarted.WhileSubscribed(), null)

    // Default implementations
    override suspend fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    override suspend fun skipToNext() {
        val flattened = flattenedPlaylist.value
        if (flattened.isEmpty()) return

        val nextIndex = when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP -> _currentItemIndex.value
            PlaybackMode.SHUFFLE -> {
                if (shuffledIndices.isEmpty()) {
                    updateShuffledIndices()
                }
                currentIndexInShuffled = (currentIndexInShuffled + 1) % shuffledIndices.size
                shuffledIndices[currentIndexInShuffled]
            }

            PlaybackMode.LOOP -> (_currentItemIndex.value + 1) % flattened.size
            PlaybackMode.SEQUENTIAL -> {
                if (_currentItemIndex.value < flattened.size - 1) {
                    _currentItemIndex.value + 1
                } else {
                    -1 // End of playlist
                }
            }
        }

        if (nextIndex != -1) {
            skipTo(nextIndex)
        }
    }

    override suspend fun skipToPrevious() {
        val flattened = flattenedPlaylist.value
        if (flattened.isEmpty()) return

        val previousIndex = when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP -> _currentItemIndex.value
            PlaybackMode.SHUFFLE -> {
                if (shuffledIndices.isEmpty()) {
                    updateShuffledIndices()
                }
                currentIndexInShuffled = (currentIndexInShuffled - 1 + shuffledIndices.size) % shuffledIndices.size
                shuffledIndices[currentIndexInShuffled]
            }

            PlaybackMode.LOOP -> (_currentItemIndex.value - 1 + flattened.size) % flattened.size
            PlaybackMode.SEQUENTIAL -> {
                if (_currentItemIndex.value > 0) {
                    _currentItemIndex.value - 1
                } else {
                    -1 // Beginning of playlist
                }
            }
        }

        if (previousIndex != -1) {
            skipTo(previousIndex)
        }
    }

    // PLAY-07: Implement skipTo(index: Int, start: Boolean)
    override suspend fun skipTo(index: Int, start: Boolean) {
        if (index < 0) {
            skipToPrevious()  // D-03: handles PLAY-09
            return
        }
        skipTo(index)  // Platform-specific: plays the item at index
        if (start) play()
    }

    // Override single-arg skipTo(index: Int) to delegate to the two-arg version
    // This allows updatePlaylist(playlist, startIndex, start) to call skipTo(startIndex, start)
    override suspend fun skipTo(index: Int) {
        skipTo(index, false)
    }

    override suspend fun updatePlaylist(playlist: List<LItem>) {
        _playlist.value = playlist
        if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            updateShuffledIndices()
        }
        updateNavigationCapabilities()
    }

    override suspend fun updatePlaylist(playlist: List<LItem>, startIndex: Int, start: Boolean) {
        updatePlaylist(playlist)
        skipTo(startIndex, start)
    }

    override suspend fun clearPlaylist() {
        _playlist.value = emptyList()
        _currentItemIndex.value = 0
        shuffledIndices = emptyList()
        currentIndexInShuffled = 0
        updateNavigationCapabilities()
    }

    override suspend fun setPlaybackMode(mode: PlaybackMode) {
        if (_playbackMode.value == mode) return

        val oldMode = _playbackMode.value
        _playbackMode.value = mode

        // When switching to or from shuffle mode, we need to update the indices
        if (oldMode == PlaybackMode.SHUFFLE || mode == PlaybackMode.SHUFFLE) {
            if (mode == PlaybackMode.SHUFFLE) {
                updateShuffledIndices()
                // Update current index in shuffled list
                currentIndexInShuffled = shuffledIndices.indexOf(_currentItemIndex.value).takeIf { it >= 0 } ?: 0
            } else {
                // When leaving shuffle mode, we might want to adjust the current index
                // to match the original playlist order
                if (oldMode == PlaybackMode.SHUFFLE) {
                    _currentItemIndex.value = shuffledIndices.getOrNull(currentIndexInShuffled) ?: 0
                }
            }
        }
    }

    // Helper methods
    protected fun updateNavigationCapabilities() {
        val flattened = flattenedPlaylist.value
        _canSeek.value = _currentDuration.value > 0

        when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP -> {
                _canSkipNext.value = false
                _canSkipPrevious.value = false
            }

            PlaybackMode.SHUFFLE -> {
                _canSkipNext.value = flattened.size > 1
                _canSkipPrevious.value = flattened.size > 1
            }

            PlaybackMode.LOOP -> {
                _canSkipNext.value = flattened.size > 1
                _canSkipPrevious.value = flattened.size > 1
            }

            PlaybackMode.SEQUENTIAL -> {
                _canSkipNext.value = _currentItemIndex.value < flattened.size - 1
                _canSkipPrevious.value = _currentItemIndex.value > 0
            }
        }
    }

    protected fun updateShuffledIndices() {
        val size = _playlist.value.flatten<LAudio>().size
        shuffledIndices = (0 until size).toList().shuffled(Random.Default)
        currentIndexInShuffled = shuffledIndices.indexOf(_currentItemIndex.value).takeIf { it >= 0 } ?: 0
    }

    protected fun emitError(error: Throwable) {
        launch {
            _errors.emit(error)
            _playbackState.value = PlaybackState.Error(error)
        }
    }

    protected fun updatePlaybackState(state: PlaybackState) {
        _playbackState.value = state
    }

    // Abstract methods that platform implementations must provide
    protected abstract suspend fun playItem(item: LAudio)
}