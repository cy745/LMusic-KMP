package com.lalilu.lmusic

import org.koin.core.annotation.KoinApplication
import org.koin.dsl.KoinAppDeclaration

@KoinApplication
expect object KoinApp

expect fun KoinApp.configuration(config: KoinAppDeclaration? = null): KoinAppDeclaration