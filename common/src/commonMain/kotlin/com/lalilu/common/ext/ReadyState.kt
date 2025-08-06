package com.lalilu.common.ext

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ReadyState {
    val isReady: StateFlow<Boolean>
    fun whenReady(callback: () -> Unit)
    fun onReady()
}

fun readyStateImpl(): ReadyState = object : ReadyState {
    private val callbacks = mutableListOf<() -> Unit>()
    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady

    override fun whenReady(callback: () -> Unit) {
        if (_isReady.value) {
            callback()
        } else {
            callbacks.add(callback)
        }
    }

    override fun onReady() {
        if (isReady.value) return

        _isReady.value = true
        callbacks.forEach { runCatching { it.invoke() } }
        callbacks.clear()
    }
}