package com.lalilu.lmedia.server

import com.lalilu.common.ext.KModule
import com.lalilu.common.ext.KoinModule
import dev.whyoleg.sweetspi.ServiceProvider
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

@Module
@ServiceProvider
@ComponentScan("com.lalilu.lmedia")
object LMediaServerModule : KModule {
    override fun get(): KoinModule = this.module
}