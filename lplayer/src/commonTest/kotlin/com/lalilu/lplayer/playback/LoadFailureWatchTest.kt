package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.MediaKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 失败记账与清除的判定：记录过一次失败的歌曲，在恢复播放成功之后必须能清掉；
 * 清除之后不能反复写库；同一条错误只记一次。
 */
class LoadFailureWatchTest {
    private val failed = MediaKey("local", "a")
    private val other = MediaKey("local", "b")

    @Test fun aFreshLoadClearsAnEarlierFailureOnTheFirstRealProgress() {
        // 失败记录可能来自更早的播放段落甚至上一次会话，因此加载后就应允许清除。
        val watch = LoadFailureWatch()
        assertTrue(watch.onPositionAdvanced())
    }

    @Test fun progressClearsOnlyOncePerPlaybackEpisode() {
        val watch = LoadFailureWatch()
        assertTrue(watch.onPositionAdvanced())
        assertFalse(watch.onPositionAdvanced())
        assertFalse(watch.onPositionAdvanced())
    }

    @Test fun theSameErrorIsRecordedOnlyOnce() {
        val watch = LoadFailureWatch()
        assertTrue(watch.onStateError("AVPlayerStatusFailed"))
        assertFalse(watch.onStateError("AVPlayerStatusFailed"))
        assertFalse(watch.onStateError("AVPlayerStatusFailed"))
    }

    @Test fun aNewErrorAfterRecoveryIsRecordedAgain() {
        val watch = LoadFailureWatch()
        assertTrue(watch.onStateError("first"))
        assertFalse(watch.onStateError(null))
        assertTrue(watch.onStateError("second"))
    }

    @Test fun aFailureRecordedAfterAnEarlierClearIsClearedAgain() {
        val watch = LoadFailureWatch()
        assertTrue(watch.onPositionAdvanced())
        assertFalse(watch.onPositionAdvanced())
        // 播放中途报错：重新武装清除，用户恢复播放后必须再次清除。
        assertTrue(watch.onStateError("stream closed"))
        assertTrue(watch.onPositionAdvanced())
        assertFalse(watch.onPositionAdvanced())
    }

    @Test fun aStalledFailureOfThePlayRequestedSongNavigatesOn() {
        assertTrue(shouldNavigateAfterStalledFailure(
            failedKey = failed,
            currentItemKey = failed,
            loadedKey = failed,
            playRequestedKey = failed,
            positionAdvanced = false,
        ))
    }

    @Test fun aSongThatIsStillAdvancingIsNotSkipped() {
        assertFalse(shouldNavigateAfterStalledFailure(failed, failed, failed, failed, positionAdvanced = true))
    }

    @Test fun aPausedOrStoppedSongIsNotSkippedEvenIfItErrors() {
        assertFalse(shouldNavigateAfterStalledFailure(failed, failed, failed, playRequestedKey = null, positionAdvanced = false))
    }

    @Test fun aLateErrorFromAnotherOrAlreadyReplacedSongIsNotActedOn() {
        assertFalse(shouldNavigateAfterStalledFailure(failed, other, failed, failed, false))
        assertFalse(shouldNavigateAfterStalledFailure(failed, failed, other, failed, false))
    }

    @Test fun aStalledFailureContinuesAlongTheDirectionTheSongWasLoadedWith() {
        // 回归：iOS 原先无论怎么进入这首歌，中途失败都固定向前跳；Android 沿用最近一次导航
        // 开启的 traversal 方向。按"上一首"进入的歌曲中途失败时必须同样向后继续。
        assertEquals(
            PlaybackDirection.Backward,
            stalledFailureNavigationDirection(
                engineStillActive = true,
                loadedKeyStillMatches = true,
                queueCurrentStillMatches = true,
                loadDirection = PlaybackDirection.Backward,
            ),
        )
        assertEquals(
            PlaybackDirection.Forward,
            stalledFailureNavigationDirection(
                engineStillActive = true,
                loadedKeyStillMatches = true,
                queueCurrentStillMatches = true,
                loadDirection = PlaybackDirection.Forward,
            ),
        )
    }

    @Test fun aStalledFailureIsNotActedOnOnceTheFailedItemStoppedBeingCurrent() {
        // 三个守卫在导航真正发起时复查：迟到的错误或用户已经换歌时不能替用户做决定。
        assertNull(stalledFailureNavigationDirection(false, true, true, PlaybackDirection.Forward))
        assertNull(stalledFailureNavigationDirection(true, false, true, PlaybackDirection.Forward))
        assertNull(stalledFailureNavigationDirection(true, true, false, PlaybackDirection.Forward))
    }
}
