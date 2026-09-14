package com.lalilu.lplayer

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.common.settings.ClickPreference
import com.lalilu.common.settings.PreferenceActionContext
import com.lalilu.lplayer.playback.HistoryQueueIdentity
import com.lalilu.lplayer.playback.HistoryStorageImpl
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Uses the real settings contribution and storage, not a mirrored click handler. */
class HistoryPositionResetSettingsTest {
    private val identity = HistoryQueueIdentity(listOf("a"), listOf("local"), 0)

    @BeforeTest fun setup() {
        KVContext.registerSaver(InMemoryKVSaver(mutableMapOf()))
        startKoin { modules(module { single { Json { ignoreUnknownKeys = true } } }) }
        LPlayerKV.historyPlaybackQueue.value = ""
        LPlayerKV.historyPositionResetRequested.value = false
    }

    @AfterTest fun cleanup() {
        LPlayerKV.historyPlaybackQueue.value = ""
        LPlayerKV.historyPositionResetRequested.value = false
        stopKoin()
    }

    @Test fun realClearActionSurvivesBackgroundSamplesAndIsConsumedByRestoration() {
        val storage = HistoryStorageImpl()
        storage.saveSnapshot(identity, 42000)
        val action = provideLPlayerSettings().preferences().filterIsInstance<ClickPreference>()
            .single { it.key == "lplayer.clear_history_position" }
        action.onClick(PreferenceActionContext())
        assertTrue(LPlayerKV.historyPositionResetRequested.value)
        storage.saveSnapshot(identity, 99000)
        assertEquals(0L, storage.savedPosition())
        assertEquals(0L, storage.readSnapshot()!!.position)
        assertFalse(LPlayerKV.historyPositionResetRequested.value)
        assertEquals(0L, storage.savedPosition())
        storage.saveSnapshot(identity, 12345)
        assertEquals(12345L, storage.readSnapshot()!!.position)
    }

    @Test fun emptyStorageCannotAccidentallyConsumeTheResetRequest() {
        val storage = HistoryStorageImpl()
        storage.requestPositionReset()
        assertNull(storage.readSnapshot())
        assertTrue(LPlayerKV.historyPositionResetRequested.value)
        storage.saveSnapshot(identity, 42000)
        assertEquals(0L, storage.readSnapshot()!!.position)
    }
}
