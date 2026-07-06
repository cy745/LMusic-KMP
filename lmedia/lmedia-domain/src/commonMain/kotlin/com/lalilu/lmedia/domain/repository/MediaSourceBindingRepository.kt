package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.source.PlatformMediaSource

interface MediaSourceBindingRepository {
    fun getSources(): PlatformMediaSource
    suspend fun startBinding()
}
