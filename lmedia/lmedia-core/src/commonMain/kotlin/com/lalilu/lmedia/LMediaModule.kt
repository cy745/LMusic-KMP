package com.lalilu.lmedia

import com.lalilu.common.ext.KModule
import com.lalilu.common.ext.KoinModule
import com.lalilu.krouter.annotation.KService
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module


@Module
@KService
@ComponentScan("com.lalilu.lmedia")
object LMediaModule : KModule {
    override fun get(): KoinModule = this.module
}