package com.lalilu.common.ext

import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import platform.darwin.sysctlbyname
import platform.posix.NULL

@OptIn(DelicateCoroutinesApi::class, ExperimentalForeignApi::class)
actual fun dispatcherIO(): CoroutineDispatcher {
    var cpuCount: Int

    memScoped {
        val nCpu = cValue<UIntVar>()
        val len = cValue<ULongVarOf<ULong>>()

        sysctlbyname("hw.ncpu", nCpu, len, NULL, "0".toULong())

        cpuCount = nCpu.getPointer(this)
            .pointed.value
            .toInt()
    }

    return newFixedThreadPoolContext(
        nThreads = 64.coerceAtLeast(cpuCount)
            .coerceAtLeast(1),
        name = "IO"
    )
}