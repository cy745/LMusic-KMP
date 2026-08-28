package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private class FakeSource(
        override val name: String,
        private val media: MediaData? = null,
    ) : MediaSource {
        val store = MediaSourceStateStore()
        var mediaRequests = 0

        override val state = store.state
        override val snapshot = store.snapshot
        override val contentState = store.contentState
        override val dataSource = object : MediaDataSource {
            override suspend fun getMedia(song: LAudio): MediaData? {
                mediaRequests++
                return media
            }
        }
    }
}
