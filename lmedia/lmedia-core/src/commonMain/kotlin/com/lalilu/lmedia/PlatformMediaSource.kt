package com.lalilu.lmedia

import com.lalilu.lmedia.source.MediaSource
import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

data class PlatformMediaSource(
    val sources: List<MediaSource>
) {
    companion object {
        internal fun provide(vararg source: MediaSource): PlatformMediaSource {
            return PlatformMediaSource(source.toList())
        }
    }
}

@Single(createdAtStart = true)
fun provideMediaSource(scope: Scope): PlatformMediaSource {
    val platformMediaSource = scope.provideMediaSources().sources
    val source = scope.getKoin().getAll<MediaSource>()

    return PlatformMediaSource(platformMediaSource + source)
        .apply { sources.forEach { it.start() } }
}

expect fun Scope.provideMediaSources(): PlatformMediaSource


context(scope: Scope)
internal inline fun <reified R : MediaSource> (() -> R).reverseInject(): MediaSource = this.invoke()

context(scope: Scope)
internal inline fun <reified R : MediaSource, reified T1> ((T1) -> R).reverseInject(): MediaSource =
    this.invoke(scope.get())

context(scope: Scope)
internal inline fun <reified R : MediaSource, reified T1, reified T2> ((T1, T2) -> R).reverseInject(): MediaSource =
    this.invoke(scope.get(), scope.get())

context(scope: Scope)
internal inline fun <reified R : MediaSource, reified T1, reified T2, reified T3> ((T1, T2, T3) -> R).reverseInject(): MediaSource =
    this.invoke(scope.get(), scope.get(), scope.get())

context(scope: Scope)
internal inline fun <reified R : MediaSource, reified T1, reified T2, reified T3, reified T4> ((T1, T2, T3, T4) -> R).reverseInject(): MediaSource =
    this.invoke(scope.get(), scope.get(), scope.get(), scope.get())

context(scope: Scope)
internal inline fun <reified R : MediaSource, reified T1, reified T2, reified T3, reified T4, reified T5> ((T1, T2, T3, T4, T5) -> R).reverseInject(): MediaSource =
    this.invoke(scope.get(), scope.get(), scope.get(), scope.get(), scope.get())

