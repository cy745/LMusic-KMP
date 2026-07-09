package com.lalilu.lmedia.source

/**
 * Interface for objects that expose a [MediaSourceConfig].
 *
 * Replaces the old core [MediaSource] interface which extended
 * [com.lalilu.lmedia.domain.source.MediaSource] solely to add [config].
 * Sources that need UI configuration should implement [Configurable]
 * alongside [com.lalilu.lmedia.domain.source.MediaSource].
 */
interface Configurable {
    val config: MediaSourceConfig
}

/** Safely access [config] from any object. */
val Any.configOrNullCompat: MediaSourceConfig?
    get() = (this as? Configurable)?.config
