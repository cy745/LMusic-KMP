package com.lalilu.common.ext

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual fun dispatcherIO(): CoroutineDispatcher = Dispatchers.IO