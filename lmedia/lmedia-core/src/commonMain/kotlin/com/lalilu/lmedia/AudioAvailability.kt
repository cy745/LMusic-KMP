package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import com.lalilu.lmedia.domain.source.CachedMediaAvailability
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.AudioPlaybackPresentation
import com.lalilu.lmedia.domain.model.audioPlaybackPresentation
import com.lalilu.lmedia.domain.repository.canReadContent
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.repository.canPlay

/** Supplied once by the application; cards never access MediaSource directly. */
val LocalMediaSourceStatuses = compositionLocalOf<Map<String, SourceStatus>> { emptyMap() }
val LocalPlaybackFailures = compositionLocalOf<Map<String, PlaybackFailure>> { emptyMap() }

@Composable
fun audioPlaybackStatus(audio: LAudio): AudioPlaybackPresentation {
    val source = LocalMediaSourceStatuses.current[audio.mediaSourceName]
    val cached = source?.enabled == true && !source.enablementChanging && audio.mediaKey in CachedMediaAvailability.complete.collectAsState().value
    return audioPlaybackPresentation(
        sourceReady = source?.canReadContent == true || cached,
        available = audio.available || cached,
        failure = LocalPlaybackFailures.current[audio.playbackId],
    )
}

@Composable
fun isAudioPlayable(audio: LAudio): Boolean {
    val source = LocalMediaSourceStatuses.current[audio.mediaSourceName]
    val cached = source?.enabled == true && !source.enablementChanging && audio.mediaKey in CachedMediaAvailability.complete.collectAsState().value
    return LocalMediaSourceStatuses.current.canPlay(audio) || cached
}
