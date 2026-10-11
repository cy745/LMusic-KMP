package com.lalilu.lmusic.util

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.skydoves.compose.stability.runtime.ParameterChange
import com.skydoves.compose.stability.runtime.RecompositionEvent
import com.skydoves.compose.stability.runtime.StateChange
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugRecomposeLoggerTest {
    @Test
    fun loggingDoesNotSubscribeTheTracedScopeToStateValues() {
        val playbackPosition = mutableLongStateOf(1234L)
        val backgroundColor = mutableLongStateOf(42L)
        // User models can also read Snapshot state from toString().
        val nestedState = object {
            override fun toString(): String = "position=${playbackPosition.longValue}"
        }
        val event = RecompositionEvent(
            composableName = "PlayerScreenContent",
            tag = "PlayerScreenContent",
            recompositionCount = 2,
            parameterChanges = listOf(
                ParameterChange("currentTime", "MutableLongState", playbackPosition, playbackPosition, false, true),
                ParameterChange("model", "Model", null, nestedState, true, false),
            ),
            unstableParameters = listOf("model"),
            stateChanges = listOf(
                StateChange("background", "State", null, backgroundColor, true),
            ),
        )
        val reads = mutableListOf<Any>()
        Snapshot.observe(readObserver = { reads.add(it) }) {
            DebugRecomposeLogger.log(event)
        }
        assertEquals(0, reads.size, "Diagnostic formatting must not create composition state subscriptions")
    }
}
