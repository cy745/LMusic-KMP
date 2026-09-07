package com.lalilu.lmedia

import com.lalilu.lmedia.domain.source.MediaSourceEnablement
import org.koin.core.annotation.Single

@Single(binds = [MediaSourceEnablement::class])
class PersistentMediaSourceEnablement(
    private val kv: LMediaKV,
) : MediaSourceEnablement {
    override fun isEnabled(sourceName: String): Boolean = kv.isSourceEnabled(sourceName)

    override fun setEnabled(sourceName: String, enabled: Boolean) {
        kv.setSourceEnabled(sourceName, enabled)
    }
}
