package com.lalilu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.lalilu.extensions.koinInjectOrNull
import org.koin.core.qualifier.named

fun interface SlotState<T> {
    @Composable
    fun state(): State<T>
}

@Composable
fun <T> state(key: String, defaultValue: () -> T): State<T> {
    val state = koinInjectOrNull<SlotState<T>>(qualifier = named(key))
    return state?.state() ?: remember { mutableStateOf(defaultValue()) }
}
