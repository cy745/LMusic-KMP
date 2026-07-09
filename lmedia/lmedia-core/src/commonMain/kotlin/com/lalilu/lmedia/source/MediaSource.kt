package com.lalilu.lmedia.source

import com.lalilu.lmedia.domain.source.MediaSource as DomainMediaSource

/**
 * Core MediaSource extending the domain interface with configuration support.
 * Platform sources that need UI configuration (settings, refresh actions) should
 * implement this. Simple sources can implement [DomainMediaSource] directly.
 */
interface MediaSource : DomainMediaSource {
    val config: MediaSourceConfig
        get() = MediaSourceConfig(key = name, name = name)
}

