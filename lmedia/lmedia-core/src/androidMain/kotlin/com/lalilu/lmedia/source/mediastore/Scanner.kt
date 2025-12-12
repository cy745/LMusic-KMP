package com.lalilu.lmedia.source.mediastore

import com.lalilu.lmedia.entity.Snapshot

interface Scanner {
    fun scan(): Snapshot
}