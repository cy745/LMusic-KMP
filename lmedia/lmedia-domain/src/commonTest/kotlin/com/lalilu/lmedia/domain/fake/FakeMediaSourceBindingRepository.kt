package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource

class FakeMediaSourceBindingRepository : MediaSourceBindingRepository {
    private var source: PlatformMediaSource = PlatformMediaSource(emptyList())

    fun withSource(source: PlatformMediaSource) {
        this.source = source
    }

    override fun getSources(): PlatformMediaSource = source

    override suspend fun startBinding() {
        // no-op
    }
}
