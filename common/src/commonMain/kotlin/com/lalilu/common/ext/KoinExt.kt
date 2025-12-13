package com.lalilu.common.ext

import dev.whyoleg.sweetspi.Service
import org.koin.core.module.Module

typealias KoinModule = Module

@Service
interface KModule {
    fun get(): KoinModule
}