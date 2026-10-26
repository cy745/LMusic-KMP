package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Cache owner publishes verified complete content; UI still checks source enablement. Empty on unsupported platforms. */
object CachedMediaAvailability {
    private val mutable = MutableStateFlow<Set<MediaKey>>(emptySet())
    val complete = mutable.asStateFlow()
    fun publish(key: MediaKey, available: Boolean) {
        mutable.update { if (available) it + key else it - key }
    }
    fun reset() { mutable.value = emptySet() }
}
