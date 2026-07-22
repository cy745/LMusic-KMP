package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackEngineRouterTest {

    private val dummyAudio = LAudio(id = "test_1", mediaSourceName = "TestSource")

    @Test
    fun `selectEngine returns first matching engine by priority`() {
        val engineA = FakeEngine(name = "EngineA", canHandleResult = false)
        val engineB = FakeEngine(name = "EngineB", canHandleResult = true)
        val engineC = FakeEngine(name = "EngineC", canHandleResult = true)

        val router = PlaybackEngineRouter(listOf(engineA, engineB, engineC))
        val result = router.selectEngine(MediaData.Url("http://test"), dummyAudio)

        assertNotNull(result)
        assertEquals("EngineB", (result as FakeEngine).name)
    }

    @Test
    fun `selectEngine returns null when no engine matches`() {
        val engine = FakeEngine(name = "NeverMatch", canHandleResult = false)
        val router = PlaybackEngineRouter(listOf(engine))

        val result = router.selectEngine(MediaData.Url("http://test"), dummyAudio)
        assertNull(result)
    }

    @Test
    fun `selectEngine returns null for empty engine list`() {
        val router = PlaybackEngineRouter(emptyList())
        assertNull(router.selectEngine(MediaData.Url("http://test"), dummyAudio))
    }

    @Test
    fun `selectEngine passes mediaData and audio to canHandle`() {
        val engine = FakeEngine(canHandleResult = true)
        val router = PlaybackEngineRouter(listOf(engine))

        val mediaData = MediaData.Bytes(byteArrayOf(1, 2, 3))
        router.selectEngine(mediaData, dummyAudio)

        assertTrue(engine.events.any { it.startsWith("canHandle(Bytes)") })
    }

    @Test
    fun `allEngines exposes registered list`() {
        val engines = listOf(
            FakeEngine(name = "A"),
            FakeEngine(name = "B"),
        )
        val router = PlaybackEngineRouter(engines)
        assertEquals(2, router.allEngines.size)
        assertEquals("A", (router.allEngines[0] as FakeEngine).name)
        assertEquals("B", (router.allEngines[1] as FakeEngine).name)
    }

    @Test
    fun `canHandle returns false for Url engine must not match`() {
        val engine = FakeEngine(canHandleResult = false)
        val router = PlaybackEngineRouter(listOf(engine))

        assertNull(router.selectEngine(MediaData.Url("http://fail"), dummyAudio))
    }

    @Test
    fun `all engines canHandle checks are called in order`() {
        val engineA = FakeEngine(name = "A", canHandleResult = false)
        val engineB = FakeEngine(name = "B", canHandleResult = false)
        val engineC = FakeEngine(name = "C", canHandleResult = true)
        val router = PlaybackEngineRouter(listOf(engineA, engineB, engineC))

        router.selectEngine(MediaData.Url("http://test"), dummyAudio)

        // engineA and engineB were asked but rejected; engineC matched
        assertTrue((engineA.events.firstOrNull() ?: "").startsWith("canHandle"))
        assertTrue((engineB.events.firstOrNull() ?: "").startsWith("canHandle"))
        assertTrue((engineC.events.firstOrNull() ?: "").startsWith("canHandle"))
    }
}
