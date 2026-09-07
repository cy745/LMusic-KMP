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

@OptIn(ExperimentalCoroutinesApi::class)
class MediaContentResolverTest {
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

    private class FakeSource(
        override val name: String,
        private val media: MediaData? = null,
    ) : MediaSource {
        val store = MediaSourceStateStore()
        var mediaRequests = 0
        var pictureRequests = 0
        var lyricRequests = 0

        override val state = store.state
        override val snapshot = store.snapshot
        override val contentState = store.contentState
        override val dataSource = object : MediaDataSource {
            override suspend fun getMedia(song: LAudio): MediaData? {
                mediaRequests++
                return media
            }

            override suspend fun getPicture(
                song: LAudio,
                options: MediaFetchOptions,
            ): MediaData? {
                pictureRequests++
                return media
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
