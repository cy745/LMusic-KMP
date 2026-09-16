package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlin.test.Test
import kotlin.test.assertEquals

/** 允许尝试的槽位规则：可用、来源就绪、且没有本来源的失败记录。 */
class PlayablePlaybackSlotsTest {
    private val readyLocal = LAudio(id = "1", mediaSourceName = "local", available = true)
    private val unavailable = LAudio(id = "2", mediaSourceName = "local", available = false)
    private val notReadySource = LAudio(id = "3", mediaSourceName = "offline", available = true)
    private val failed = LAudio(id = "4", mediaSourceName = "local", available = true)
    private val sameRawIdOtherSource = LAudio(id = "4", mediaSourceName = "sandbox", available = true)

    private val list = listOf(readyLocal, unavailable, notReadySource, failed, sameRawIdOtherSource)

    @Test fun onlyAvailableReadyAndNotFailedSlotsMayBeAttempted() {
        val slots = playablePlaybackSlots(
            list = list,
            recordedFailures = setOf(failed.playbackId),
            sourceReady = { it.mediaSourceName != "offline" },
        )
        // 0 允许；1 不可用；2 来源未就绪；3 已记录失败；4 与 3 同原始 ID 但另一个来源，仍是允许的。
        assertEquals(setOf(0, 4), slots)
    }

    @Test fun failuresAreSourceQualifiedAndDoNotLeakAcrossSources() {
        val slots = playablePlaybackSlots(
            list = listOf(sameRawIdOtherSource),
            recordedFailures = setOf(failed.playbackId),
            sourceReady = { true },
        )
        assertEquals(setOf(0), slots)
    }

    @Test fun nothingIsPlayableWhenEverySourceIsUnready() {
        assertEquals(
            emptySet(),
            playablePlaybackSlots(list, emptySet()) { false },
        )
    }
}
