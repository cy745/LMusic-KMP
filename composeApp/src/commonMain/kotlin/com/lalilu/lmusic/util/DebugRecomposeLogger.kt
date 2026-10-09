package com.lalilu.lmusic.util

import co.touchlab.kermit.Logger
import com.skydoves.compose.stability.runtime.RecompositionEvent
import com.skydoves.compose.stability.runtime.RecompositionLogger

object DebugRecomposeLogger : RecompositionLogger {

    private val tag = "Recomposition"

    override fun log(event: RecompositionEvent) {
        val tagSuffix = if (event.tag.isNotEmpty()) " (tag: ${event.tag})" else ""
        val durationStr = if (event.durationNanos > 0) {
            val ms = event.durationNanos / 1_000_000
            val fraction = (event.durationNanos % 1_000_000) / 10_000
            " ($ms.${fraction.toString().padStart(2, '0')}ms)"
        } else {
            ""
        }

        Logger.i(
            tag = tag,
            messageString = "[Recomposition #${event.recompositionCount}] " +
                    "${event.composableName}$tagSuffix$durationStr",
        )

        val lines = buildTreeLines(event)
        lines.forEachIndexed { index, line ->
            val prefix = if (index == lines.size - 1) "  └─" else "  ├─"
            Logger.i(
                tag = tag,
                messageString = "$prefix $line"
            )
        }
    }

    private fun buildTreeLines(event: RecompositionEvent): List<String> {
        val lines = mutableListOf<String>()

        event.parameterChanges.forEach { change ->
            val status = when {
                change.changed -> {
                    val oldStr = safeToString(change.oldValue)
                    val newStr = safeToString(change.newValue)
                    "changed ($oldStr → $newStr)"
                }

                change.stable -> "stable (${safeToString(change.newValue)})"
                else -> "unstable (${safeToString(change.newValue)})"
            }
            lines.add("[param] ${change.name}: ${change.type} $status")
        }

        event.stateChanges.filter { it.changed }.forEach { change ->
            val oldStr = safeToString(change.oldValue)
            val newStr = safeToString(change.newValue)
            lines.add("[state] ${change.name}: ${change.type} changed ($oldStr → $newStr)")
        }

        if (event.unstableParameters.isNotEmpty()) {
            lines.add("Unstable parameters: ${event.unstableParameters}")
        }

        val changedStates = event.stateChanges.filter { it.changed }.map { it.name }
        if (changedStates.isNotEmpty()) {
            lines.add("State changes: $changedStates")
        }

        return lines
    }

    /**
     * Safely converts a value to string, handling function types and reflection errors.
     * Falls back to a simple representation if toString() throws an exception.
     */
    private fun safeToString(value: Any?): String {
        if (value == null) return "null"

        return try {
            value.toString()
        } catch (e: Throwable) {
            // Fallback for any toString() failures (including reflection errors)
            // On iOS/native, we don't have javaClass.simpleName, so use a simpler approach
            "Object@${value.hashCode().toString(16)}"
        }
    }
}
