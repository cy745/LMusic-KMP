package com.lalilu.lmusic.util

import com.skydoves.compose.stability.runtime.RecompositionEvent
import com.skydoves.compose.stability.runtime.RecompositionLogger
import io.github.oshai.kotlinlogging.KotlinLogging

object DebugRecomposeLogger : RecompositionLogger {

    private val tag = "Recomposition"
    private val logger = KotlinLogging.logger(tag)

    override fun log(event: RecompositionEvent) {
        val tagSuffix = if (event.tag.isNotEmpty()) " (tag: ${event.tag})" else ""

        logger.info { "Recomposition #${event.recompositionCount} ${event.composableName}$tagSuffix" }

        // Log parameter changes
        event.parameterChanges.forEachIndexed { index, change ->
            val isLast = index == event.parameterChanges.size - 1
            val prefix = if (isLast) "  └─" else "  ├─"

            val status = when {
                change.changed -> {
                    val oldStr = safeToString(change.oldValue)
                    val newStr = safeToString(change.newValue)
                    "changed ($oldStr → $newStr)"
                }

                change.stable -> "stable (${safeToString(change.newValue)})"
                else -> "unstable (${safeToString(change.newValue)})"
            }

            logger.info { "$prefix ${change.name}: ${change.type} $status" }
        }

        // Log unstable parameters summary
        if (event.unstableParameters.isNotEmpty()) {
            logger.info { "  └─ Unstable parameters summary: ${event.unstableParameters}" }
        }
    }

    /**
     * Safely converts a value to string, handling reflection errors.
     * Falls back to a simple representation if toString() throws an exception.
     */
    private fun safeToString(value: Any?): String {
        if (value == null) return "null"

        return try {
            value.toString()
        } catch (e: Throwable) {
            // Fallback for any toString() failures (including reflection errors)
            "${value::class.qualifiedName}@${value.hashCode().toString(16)}"
        }
    }
}