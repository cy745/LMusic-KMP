package com.lalilu.lplayer

import com.lalilu.common.ext.KModule
import com.lalilu.common.ext.KoinModule
import com.lalilu.krouter.annotation.KService
import com.lalilu.llyricview.LLyricViewModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

@Module(includes = [LLyricViewModule::class])
@KService
@ComponentScan("com.lalilu.lplayer")
object LPlayerModule : KModule {
    override fun get(): KoinModule = module.also { it.includes(playbackModule) }
}

/**
 * playback各平台注入实现
 */
expect val playbackModule: org.koin.core.module.Module