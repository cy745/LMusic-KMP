package com.lalilu.lmusic

import org.koin.core.annotation.KoinApplication
import org.koin.dsl.KoinAppDeclaration
import org.koin.ksp.generated.koinConfiguration

@KoinApplication
actual object KoinApp

actual fun KoinApp.configuration(config: KoinAppDeclaration?): KoinAppDeclaration {
    return koinConfiguration(config)
}