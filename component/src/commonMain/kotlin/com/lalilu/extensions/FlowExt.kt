package com.lalilu.extensions

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 将Flow转换为State
 */
fun <T> Flow<T>.toState(scope: CoroutineScope): State<T?> {
    return mutableStateOf<T?>(null).also { state ->
        this.onEach { state.value = it }.launchIn(scope)
    }
}

/**
 * 将Flow转换为State，附带初始值
 */
fun <T> Flow<T>.toState(
    defaultValue: T,
    scope: CoroutineScope,
): State<T> {
    return mutableStateOf(defaultValue).also { state ->
        this.onEach { state.value = it }.launchIn(scope)
    }
}

/**
 * 将Flow转换为MutableState
 */
fun <T> Flow<T>.toMutableState(scope: CoroutineScope): MutableState<T?> {
    return mutableStateOf<T?>(null).also { state ->
        this.onEach { state.value = it }.launchIn(scope)
    }
}

/**
 * 将Flow转换为MutableState
 */
fun <T> Flow<T>.toMutableState(defaultValue: T, scope: CoroutineScope): MutableState<T> {
    return mutableStateOf(defaultValue).also { state ->
        this.onEach { state.value = it }.launchIn(scope)
    }
}