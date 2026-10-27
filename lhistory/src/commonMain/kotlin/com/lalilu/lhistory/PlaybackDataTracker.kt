@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.lalilu.lhistory

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lhistory.repository.HistoryRepository
import com.lalilu.lplayer.playback.IPlaybackDataTracker
import com.lalilu.lplayer.playback.ListeningObservation
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.TimeSource

@Single(binds = [IPlaybackDataTracker::class, PlaybackDataTracker::class])
class PlaybackDataTracker(private val historyRepository: HistoryRepository) : IPlaybackDataTracker {
    private val received = MutableStateFlow(0L)
    private val operation = MutableStateFlow("idle")
    override val diagnostics get() = "observations=${received.value}, operation=${operation.value}"
    override suspend fun inspect() = "$diagnostics, ${historyRepository.inspect()}"
    private val scope = CoroutineScope(Dispatchers.io + SupervisorJob())
    private val origin = TimeSource.Monotonic.markNow()
    private data class Message(val state: ListeningObservation?, val wall: Long, val mono: Long, val clear: CompletableDeferred<Unit>? = null)
    private val inbox = Channel<Message>(Channel.UNLIMITED)

    init {
        scope.launch {
            val recorder = ListeningRecorder({ row, days ->
                operation.value = "saving"
                try { historyRepository.saveSession(row, days).also { operation.value = "saved row=$it" } }
                catch (error: Exception) { operation.value = "save failed: ${error.message}"; throw error }
            })
            var latest: ListeningObservation? = null
            for (message in inbox) {
                message.clear?.let { done ->
                    try { operation.value = "clearing"; historyRepository.clearHistories(); recorder.cleared(); operation.value = "cleared"; done.complete(Unit) }
                    catch (cancelled: CancellationException) { done.cancel(cancelled); throw cancelled }
                    catch (error: Exception) { operation.value = "clear failed: ${error.message}"; done.completeExceptionally(error) }
                }
                if (message.clear != null) continue
                message.state?.let { latest = it }
                val state = latest ?: continue
                try { recorder.accept(state, message.wall, message.mono) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Logger.e("Listening history write failed; retrying on next observation", error) }
            }
        }
        scope.launch { while (isActive) { delay(5000); enqueue(null) } }
    }

    suspend fun clearHistory() {
        val done = CompletableDeferred<Unit>()
        inbox.send(Message(null, Clock.System.now().toEpochMilliseconds(), origin.elapsedNow().inWholeMilliseconds, done))
        done.await()
    }

    private fun enqueue(state: ListeningObservation?) {
        inbox.trySend(Message(state, Clock.System.now().toEpochMilliseconds(), origin.elapsedNow().inWholeMilliseconds))
    }
    override fun onObservation(observation: ListeningObservation) {
        received.update { it + 1 }
        enqueue(observation)
    }

    // Legacy callbacks are intentionally inert: only the shared core owns recording.
    override fun onMediaItemTransition(mediaId: String?, title: String?, isRepeating: Boolean, isNormalTransition: Boolean) = Unit
    override fun onIsPlayingChanged(isPlaying: Boolean) = Unit
}
