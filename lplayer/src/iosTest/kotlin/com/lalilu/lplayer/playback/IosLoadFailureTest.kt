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

    @Test fun domainAndCodeMustAppearAsAPair() {
        // 257 属于别的 domain：它不是 Cocoa 权限错误，而是 OSStatus 的解码失败。
        val foreignDomain = classifyIosLoadFailure(
            IllegalStateException("Error Domain=NSOSStatusErrorDomain Code=257")
        )
        assertEquals(PlaybackFailureReason.UnsupportedFormat, foreignDomain)
        assertFalse(foreignDomain == PlaybackFailureReason.PermissionDenied)
        // 只有 code、没有配对 domain 的文本不参与判定。
        assertEquals(
            PlaybackFailureReason.Unknown,
            classifyIosLoadFailure(IllegalStateException("failed with Code=257")),
        )
        // 顶层 domain/code 在前时以顶层为准，不被 UserInfo 里嵌套的 domain/code 覆盖。
        assertEquals(
            PlaybackFailureReason.Network,
            classifyIosLoadFailure(IllegalStateException(
                "Error Domain=NSURLErrorDomain Code=-1009 \"offline\" " +
                    "UserInfo={NSUnderlyingError=Error Domain=NSCocoaErrorDomain Code=257}"
            )),
        )
    }

    @Test fun theLocalizedNSErrorFormIsAlsoUnderstood() {
        // MusicKit 侧用的是 localizedDescription，形如 "(NSURLErrorDomain error -1009.)"。
        assertEquals(
            PlaybackFailureReason.Network,
            classifyIosLoadFailure(IllegalStateException(
                "The operation couldn't be completed. (NSURLErrorDomain error -1009.)"
            )),
        )
    }

    @Test fun osStatusFailuresMeanTheDataCouldNotBeDecoded() {
        assertEquals(
            PlaybackFailureReason.UnsupportedFormat,
            classifyIosLoadFailure(IllegalStateException(
                "Error Domain=NSOSStatusErrorDomain Code=1954115647 \"(null)\""
            )),
        )
        assertEquals(
            PlaybackFailureReason.UnsupportedFormat,
            classifyIosLoadFailure(IllegalStateException(
                "The operation couldn't be completed. (OSStatus error 1954115647.)"
            )),
        )
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
