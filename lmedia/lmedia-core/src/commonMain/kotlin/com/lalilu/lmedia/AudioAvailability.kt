package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
fun audioPlaybackStatus(audio: LAudio): AudioPlaybackPresentation = audioPlaybackPresentation(
    sourceReady = LocalMediaSourceStatuses.current[audio.mediaSourceName]?.canReadContent == true,
    available = audio.available,
    failure = LocalPlaybackFailures.current[audio.playbackId],
)

@Composable
fun isAudioPlayable(audio: LAudio): Boolean = LocalMediaSourceStatuses.current.canPlay(audio)
