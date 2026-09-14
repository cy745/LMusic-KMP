package com.lalilu.lplayer.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** A pause may prepare silently; a stop must not reopen media for an older request. */
internal class PreparationControl {
    private data class State(val revision: Long = 0, val stoppedAt: Long = 0)
    private val state = MutableStateFlow(State())
    fun ticket(): Long = state.value.revision
    fun pause() = change(stopped = false)
    fun stop() = change(stopped = true)

    fun playIntent(ticket: Long, requested: Boolean): Boolean? {
        val current = state.value
        if (current.stoppedAt > ticket) return null
        return requested && current.revision == ticket
    }

    private fun change(stopped: Boolean) {
        while (true) {
            val before = state.value
            val revision = before.revision + 1
            if (state.compareAndSet(before, State(revision, if (stopped) revision else before.stoppedAt))) return
        }
    }
}
