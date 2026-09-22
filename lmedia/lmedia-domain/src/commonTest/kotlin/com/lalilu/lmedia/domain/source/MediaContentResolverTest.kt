package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MediaContentResolverTest {
    @Test
    fun readySourceWithMissingSongIsNotASourceReadinessFailure() = runTest {
        val target = FakeSource("target")
        target.store.content.ready()
        assertFailsWith<AudioMediaMissingException> {
            PlatformMediaSource(listOf(target)).resolveMediaData(
                LAudio(id = "missing", mediaSourceName = "target")
            )
        }
        assertEquals(1, target.mediaRequests)
    }

    @Test
    fun onlyTargetSourceReadinessIsRequired() = runTest {
        val unrelated = FakeSource("slow")
        unrelated.store.content.preparing(preserveReady = false)
        val target = FakeSource("target", MediaData.Url("file:///music.mp3"))
        target.store.content.ready()
        val platform = PlatformMediaSource(listOf(unrelated, target))

        val result = platform.resolveMediaData(
            audio = LAudio(id = "song", title = "Song", mediaSourceName = "target"),
        )

        assertEquals(MediaData.Url("file:///music.mp3"), result)
        assertEquals(0, unrelated.mediaRequests)
        assertEquals(1, target.mediaRequests)
    }

    @Test
    fun targetCanBecomeReadyAfterRequestStarts() = runTest {
        val target = FakeSource("target", MediaData.Url("file:///music.mp3"))
        target.store.content.preparing(preserveReady = false)
        val platform = PlatformMediaSource(listOf(target))
        val resolving = async {
            platform.resolveMediaData(
                audio = LAudio(id = "song", title = "Song", mediaSourceName = "target"),
            )
        }
        runCurrent()

        target.store.content.ready()

        assertEquals(MediaData.Url("file:///music.mp3"), resolving.await())
    }

    @Test
    fun unavailableTargetFailsWithoutWaitingForOtherSources() = runTest {
        val target = FakeSource("target")
        target.store.content.unavailable("permission denied")

        assertFailsWith<MediaContentUnavailableException> {
            PlatformMediaSource(listOf(target)).resolveMediaData(
                audio = LAudio(id = "song", title = "Song", mediaSourceName = "target"),
            )
        }
    }

    @Test
    fun disabledTargetFailsEvenWhenItsOldContentWasReady() = runTest {
        val target = FakeSource("target", MediaData.Url("file:///music.mp3"))
        target.store.content.ready()
        val platform = PlatformMediaSource(
            sources = listOf(target),
            enablement = object : MediaSourceEnablement {
                override fun isEnabled(sourceName: String): Boolean = false
                override fun setEnabled(sourceName: String, enabled: Boolean) = Unit
            },
        )

        assertFailsWith<MediaContentUnavailableException> {
            platform.resolveMediaData(
                audio = LAudio(id = "song", title = "Song", mediaSourceName = "target"),
            )
        }
        assertEquals(0, target.mediaRequests)
    }

    @Test
    fun disabledTargetDoesNotServePictureOrLyric() = runTest {
        val target = FakeSource("target", MediaData.Url("file:///music.mp3"))
        target.store.content.ready()
        val platform = PlatformMediaSource(
            sources = listOf(target),
            enablement = MutableEnablement(enabled = false),
        )
        val audio = LAudio(id = "song", title = "Song", mediaSourceName = "target")

        assertNull(platform.resolvePictureData(audio))
        assertNull(platform.resolveLyricData(audio))
        assertEquals(0, target.pictureRequests)
        assertEquals(0, target.lyricRequests)
    }

    @Test
    fun pictureWithoutReadinessWaitReadsEnabledSourceDirectly() = runTest {
        val target = FakeSource("target", MediaData.Url("file:///cover.jpg"))
        target.store.content.preparing(preserveReady = false)
        val platform = PlatformMediaSource(listOf(target))
        val audio = LAudio(id = "song", title = "Song", mediaSourceName = "target")

        assertEquals(MediaData.Url("file:///cover.jpg"), platform.resolvePictureData(audio))
        assertEquals(1, target.pictureRequests)
    }

    @Test
    fun pictureReadinessTimeoutFallsThroughToDataSource() = runTest {
        val target = FakeSource("target", MediaData.Url("file:///cover.jpg"))
        target.store.content.preparing(preserveReady = false)
        val platform = PlatformMediaSource(listOf(target))
        val audio = LAudio(id = "song", title = "Song", mediaSourceName = "target")

        assertEquals(
            MediaData.Url("file:///cover.jpg"),
            platform.resolvePictureData(audio, timeoutMillis = 100L),
        )
        assertEquals(1, target.pictureRequests)
    }

    @Test
    fun sourceDisabledWhileWaitingDoesNotServeContent() = runTest {
        val target = FakeSource("target", MediaData.Url("file:///music.mp3"))
        target.store.content.preparing(preserveReady = false)
        val enablement = MutableEnablement(enabled = true)
        val platform = PlatformMediaSource(listOf(target), enablement)
        val audio = LAudio(id = "song", title = "Song", mediaSourceName = "target")
        val picture = async { platform.resolvePictureData(audio, timeoutMillis = 5_000L) }
        runCurrent()

        enablement.enabled = false
        target.store.content.ready()

        assertNull(picture.await())
        assertEquals(0, target.pictureRequests)
    }

    @Test
    fun cancelledPictureRequestDoesNotFallThroughToDataSource() = runTest {
        val target = FakeSource("target", MediaData.Url("file:///music.mp3"))
        target.store.content.preparing(preserveReady = false)
        val platform = PlatformMediaSource(listOf(target))
        val audio = LAudio(id = "song", title = "Song", mediaSourceName = "target")
        val picture = async { platform.resolvePictureData(audio, timeoutMillis = 5_000L) }
        runCurrent()

        picture.cancelAndJoin()
        target.store.content.ready()

        assertEquals(0, target.pictureRequests)
    }

    @Test
    fun pictureStillComingIsWaitedForInsteadOfBeingReportedMissing() = runTest {
        // 网络歌曲的封面要等提取跑完才有：第一次读不到不代表"没有"，界面这时已经在按失败渲染了，
        // 而用户看到的就是"切歌先闪一下底色，再跳成封面"。
        val target = FakeSource("target", MediaData.Url("file:///cover.jpg"), pictureFromRequest = 2)
        target.store.content.ready()
        val platform = PlatformMediaSource(listOf(target))
        val audio = LAudio(id = "song", title = "Song", mediaSourceName = "target")

        assertEquals(
            MediaData.Url("file:///cover.jpg"),
            platform.resolvePictureData(audio, timeoutMillis = 5_000L),
        )
        assertEquals(2, target.pictureRequests)
    }

    @Test
    fun pictureThatNeverComesGivesUpAfterTheBudget() = runTest {
        val target = FakeSource("target", pictureFromRequest = Int.MAX_VALUE)
        target.store.content.ready()
        val platform = PlatformMediaSource(listOf(target))
        val audio = LAudio(id = "song", title = "Song", mediaSourceName = "target")

        assertNull(platform.resolvePictureData(audio, timeoutMillis = 1_000L))
        // 预算内至少试过不止一次，而不是读完一次就下结论
        assertTrue(target.pictureRequests > 1, "expected retries, got ${target.pictureRequests}")
    }

    @Test
    fun pictureKnownToBeMissingIsNotWaitedFor() = runTest {
        // 来源明确说"这首歌就是没有封面"时，等下去只会让界面多显示一会儿上一张
        val target = FakeSource("target", pictureFromRequest = Int.MAX_VALUE, pending = false)
        target.store.content.ready()
        val platform = PlatformMediaSource(listOf(target))
        val audio = LAudio(id = "song", title = "Song", mediaSourceName = "target")

        assertNull(platform.resolvePictureData(audio, timeoutMillis = 5_000L))
        assertEquals(1, target.pictureRequests)
    }

    private class FakeSource(
        override val name: String,
        private val media: MediaData? = null,
        /** 第几次请求开始返回封面（模拟"封面要等一会儿才出现"）。 */
        private val pictureFromRequest: Int = 1,
        private val pending: Boolean = true,
    ) : MediaSource {
        val store = MediaSourceStateStore()
        var mediaRequests = 0
        var pictureRequests = 0
        var lyricRequests = 0

        override val state = store.state
        override val snapshot = store.snapshot
        override val contentState = store.contentState
        override val dataSource: MediaDataSource = object : MediaDataSource, MediaItemContentPending {
            override fun isItemContentPending(audio: LAudio): Boolean = pending

            override suspend fun getMedia(song: LAudio): MediaData? {
                mediaRequests++
                return media
            }

            override suspend fun getPicture(
                song: LAudio,
                options: MediaFetchOptions,
            ): MediaData? {
                pictureRequests++
                return media.takeIf { pictureRequests >= pictureFromRequest }
            }

            override suspend fun getLyric(song: LAudio): String? {
                lyricRequests++
                return "lyric"
            }
        }
    }

    private class MutableEnablement(var enabled: Boolean) : MediaSourceEnablement {
        override fun isEnabled(sourceName: String): Boolean = enabled
        override fun setEnabled(sourceName: String, enabled: Boolean) {
            this.enabled = enabled
        }
    }
}
