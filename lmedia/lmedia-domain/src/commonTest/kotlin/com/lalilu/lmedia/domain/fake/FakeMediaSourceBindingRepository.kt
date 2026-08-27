package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.MediaLibrarySummary
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class FakeMediaSourceBindingRepository : MediaSourceBindingRepository {
    private var source: PlatformMediaSource = PlatformMediaSource(emptyList())
    override val states: MutableStateFlow<Map<String, SourceStatus>> = MutableStateFlow(emptyMap())
    override val summary: MutableStateFlow<MediaLibrarySummary> = MutableStateFlow(MediaLibrarySummary())

    fun withSource(source: PlatformMediaSource) {
        this.source = source
    }

    override fun getSources(): PlatformMediaSource = source

    override fun observeSource(name: String): Flow<SourceStatus?> = states.map { it[name] }

    override suspend fun startBinding() {
        // no-op
    }
}
