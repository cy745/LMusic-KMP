package com.lalilu.lmedia.source

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module


@Module
@Configuration("default")
@ComponentScan("com.lalilu.lmedia")
object LMediaClientModule