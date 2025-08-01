package com.lalilu.common.ext

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private val ioDispatcher by lazy { dispatcherIO() }
val Dispatchers.io: CoroutineDispatcher
    get() = ioDispatcher

expect fun dispatcherIO(): CoroutineDispatcher