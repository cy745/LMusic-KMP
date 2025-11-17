package com.lalilu.common.ext


import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun dispatcherIO(): CoroutineDispatcher = Dispatchers.IO