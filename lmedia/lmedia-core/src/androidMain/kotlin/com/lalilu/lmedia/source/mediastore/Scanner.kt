package com.lalilu.lmedia.source.mediastore

import com.lalilu.lmedia.entity.Snapshot

interface Scanner {
    suspend fun scan(): Snapshot
}