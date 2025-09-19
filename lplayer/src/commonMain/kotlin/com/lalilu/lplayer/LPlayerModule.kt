package com.lalilu.lplayer

import com.lalilu.llyricview.LLyricViewModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module


@Module(includes = [LLyricViewModule::class])
@ComponentScan("com.lalilu.lplayer")
object LPlayerModule