package com.lalilu.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf


val LocalPassthroughHolder = staticCompositionLocalOf { mutableStateMapOf<String, Any>() }

object PassThroughHelper {

    @Composable
    fun Passthrough(
        vararg value: Pair<String, Any>,
        content: @Composable () -> Unit
    ) {
        val map = LocalPassthroughHolder.current
        val newMap = remember(map) {
            mutableStateMapOf<String, Any>().also {
                it.putAll(map)
                it.putAll(value)
            }
        }

        CompositionLocalProvider(LocalPassthroughHolder provides newMap) {
            content()
        }
    }

    @Composable
    inline fun <reified T> getValue(key: String, default: T): T {
        return LocalPassthroughHolder.current[key] as? T ?: default
    }
}
