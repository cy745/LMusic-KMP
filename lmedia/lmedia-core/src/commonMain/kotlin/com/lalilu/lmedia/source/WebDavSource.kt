package com.lalilu.lmedia.source

import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow

class WebDavSource : MediaSource {
    override val name: String = "WebDavSource"

    override fun source(): Flow<Snapshot> {
        // TODO
        return super.source()
    }
}