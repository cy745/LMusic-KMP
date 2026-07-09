package com.lalilu.lmedia.source.mediastore

import com.lalilu.lmedia.domain.source.Snapshot

interface Scanner {
    suspend fun scan(): Snapshot
}