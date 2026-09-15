package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.source.AudioMediaMissingException
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** iOS 只按 error domain/code 归类，且绝不把 NSError 描述持久化到界面上。 */
class IosLoadFailureTest {
    @Test fun missingLocalMediaIsReportedAsFileMissing() {
        val error = IllegalStateException("wrapper", AudioMediaMissingException())
        assertEquals(PlaybackFailureReason.FileMissing, classifyIosLoadFailure(error))
    }

    @Test fun urlErrorDomainIsReportedAsNetwork() {
        val error = IllegalStateException(
            "Error Domain=NSURLErrorDomain Code=-1009 \"The Internet connection appears to be offline.\" " +
                "UserInfo={NSLocalizedDescription=The Internet connection appears to be offline.}"
        )
        assertEquals(PlaybackFailureReason.Network, classifyIosLoadFailure(error))
    }

    @Test fun cocoaPermissionAndMissingFileCodesAreDistinguished() {
        assertEquals(
            PlaybackFailureReason.PermissionDenied,
            classifyIosLoadFailure(IllegalStateException(
                "Error Domain=NSCocoaErrorDomain Code=257 \"The file couldn't be opened.\""
            )),
        )
        assertEquals(
            PlaybackFailureReason.FileMissing,
            classifyIosLoadFailure(IllegalStateException(
                "Error Domain=NSCocoaErrorDomain Code=260 \"The file couldn't be opened.\""
            )),
        )
    }

    @Test fun aFileUrlSeenThroughTheUrlDomainIsNotClaimedAsANetworkFailure() {
        // AVFoundation 对不存在的本地文件也可能报 NSURLErrorDomain Code=-1100。
        // 类别错误比通用提示更糟，因此这里保持 Unknown，不推测成网络问题。
        val error = IllegalStateException(
            "Error Domain=NSURLErrorDomain Code=-1100 \"The requested URL was not found on this server.\""
        )
        assertEquals(PlaybackFailureReason.Unknown, classifyIosLoadFailure(error))
    }

    @Test fun unknownDescriptionsAreNotGuessedEvenWhenTheyContainPathsOrCredentials() {
        val error = IllegalStateException("https://user:secret@example.com/stream.mp3 failed")
        assertEquals(PlaybackFailureReason.Unknown, classifyIosLoadFailure(error))
    }

    @Test fun onlyTheCurrentReadySongRecordsAnActualFailure() {
        val failure = IllegalStateException("decode failed")
        assertTrue(shouldRecordIosLoadFailure("sandbox:1", "sandbox:1", sourceReady = true, error = failure))
    }

    @Test fun cancellationAndUnreadySourceAreNeverRecordedAsSongFailures() {
        assertFalse(shouldRecordIosLoadFailure(
            "sandbox:1", "sandbox:1", sourceReady = true, error = CancellationException("replaced"),
        ))
        assertFalse(shouldRecordIosLoadFailure(
            "sandbox:1", "sandbox:1", sourceReady = false, error = IllegalStateException("load failed"),
        ))
    }

    @Test fun aLateFailureFromAnotherSongIsNotRecorded() {
        assertFalse(shouldRecordIosLoadFailure(
            playbackId = "local:2",
            ticketId = "sandbox:1",
            sourceReady = true,
            error = IllegalStateException("load failed"),
        ))
        assertFalse(shouldRecordIosLoadFailure(
            playbackId = null,
            ticketId = "sandbox:1",
            sourceReady = true,
            error = IllegalStateException("load failed"),
        ))
    }
}
