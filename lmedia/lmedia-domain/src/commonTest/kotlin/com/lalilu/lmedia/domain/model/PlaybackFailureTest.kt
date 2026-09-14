package com.lalilu.lmedia.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackFailureTest {
    private val failure = PlaybackFailure(PlaybackFailureReason.Decode, 123L)

    @Test fun sourceNotReadyRemainsGrayEvenWithRememberedFailure() {
        assertEquals(AudioPlaybackPresentation.SourceNotReady, audioPlaybackPresentation(false, true, failure))
        assertEquals(AudioPlaybackPresentation.SourceNotReady, audioPlaybackPresentation(false, false, null))
    }

    @Test fun sourceRecoveryDoesNotEraseSongFailure() {
        assertEquals(AudioPlaybackPresentation.Failed(PlaybackFailureReason.Decode),
            audioPlaybackPresentation(true, true, failure))
    }

    @Test fun missingFileOnReadySourceIsFailureNotLoading() {
        assertEquals(AudioPlaybackPresentation.Failed(PlaybackFailureReason.FileMissing),
            audioPlaybackPresentation(true, false, null))
    }

    @Test fun successfulPlaybackCanReturnToNormalAfterFailureClears() {
        assertEquals(AudioPlaybackPresentation.Available, audioPlaybackPresentation(true, true, null))
    }
}
