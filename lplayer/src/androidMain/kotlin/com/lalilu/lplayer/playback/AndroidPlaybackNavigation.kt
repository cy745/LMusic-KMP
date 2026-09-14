package com.lalilu.lplayer.playback

import org.koin.core.annotation.Single

/** Service commands and app commands share one navigation attempt in the main process. */
@Single
class AndroidPlaybackNavigation {
    internal val failures = PlaybackFailureTraversal()
    internal val preparation = PreparationControl()
}
