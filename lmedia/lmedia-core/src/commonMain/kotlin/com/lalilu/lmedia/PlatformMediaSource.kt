package com.lalilu.lmedia

/**
 * PlatformMediaSource is now registered in composeApp's SharedModule.
 * See Koin.kt: single<PlatformMediaSource> { ... }
 *
 * This file intentionally left minimal. The old provideMediaSource
 * top-level @Single function (which was here) was not properly picked up
 * by the krouter KSP processor, so registration moved to SharedModule.
 */
