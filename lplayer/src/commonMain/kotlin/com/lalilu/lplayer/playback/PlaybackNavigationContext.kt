package com.lalilu.lplayer.playback

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Keep a command's direction with its coroutine, not in mutable state shared by competing commands. */
internal class PlaybackNavigationContext(val direction: PlaybackDirection) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PlaybackNavigationContext>
}
