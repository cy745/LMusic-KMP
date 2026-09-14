package com.lalilu.lmusic.repository

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.PersistentMediaSourceEnablement
import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.data.database.LMediaDao
import com.lalilu.lmedia.data.repository.AudioRepositoryImpl
import com.lalilu.lmedia.data.repository.MediaSourceBindingRepositoryImpl
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

/** Actual Room and binding repository; one DAO entry-point failure is deliberately injected. */
class SourceOperationDatabaseTest {
    private class Source : MediaSource {
        override val name = "transaction-test-source"
        override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
        override val snapshot = MutableStateFlow<Snapshot?>(null)
        override val contentState = MutableStateFlow(MediaContentState(MediaContentAvailability.Ready))
        override suspend fun deactivate() { snapshot.value = null }
    }

    @Test fun failedTransactionRestoresDatabaseBeforeDisableAndSourceCanBeEnabledAgain() = runBlocking {
        withTimeout(15_000) {
            KVContext.kvMap.clear()
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.IO + job)
            val db = requireDatabase<LMusicDatabase>(forceMemory = true)
            val realDao = db.mediaDao()
            val failNext = AtomicBoolean(false)
            val failingDao = object : LMediaDao by realDao {
                override suspend fun insert(snapshot: Snapshot, sourceName: String) {
                    if (failNext.getAndSet(false)) error("Injected DAO commit failure")
                    realDao.insert(snapshot, sourceName)
                }
            }
            val wrapped = object : ILMediaDatabase by db {
                override fun mediaDao(): LMediaDao = failingDao
            }
            val source = Source()
            val kv = LMediaKV(InMemoryKVSaver())
            val platform = PlatformMediaSource(listOf(source), PersistentMediaSourceEnablement(kv))
            val repository = MediaSourceBindingRepositoryImpl(platform, wrapped, kv, scope)
            val audios = AudioRepositoryImpl(db, platform)
            val original = LAudio(id = "transaction-song", title = "before", mediaSourceName = source.name)

            suspend fun awaitCommitted(revision: Long) {
                repository.observeSource(source.name).first {
                    (it?.commitState as? SnapshotCommitState.Committed)?.revision == revision
                }
            }

            try {
                repository.startBinding()
                source.snapshot.value = Snapshot(listOf(original), revision = 1)
                awaitCommitted(1)
                assertTrue(audios.getAudio(original.id).first()!!.available)

                val transactionEntered = CompletableDeferred<Unit>()
                val finishTransaction = CompletableDeferred<Unit>()
                val transaction = async {
                    repository.withEnabledSource(source.name) {
                        transactionEntered.complete(Unit)
                        finishTransaction.await()
                        failNext.set(true)
                        source.snapshot.value = Snapshot(emptyList(), revision = 2)
                        repository.observeSource(source.name).first {
                            (it?.commitState as? SnapshotCommitState.Failed)?.revision == 2L
                        }
                        // A failed removal has not changed the actual database row.
                        assertTrue(audios.getAudio(original.id).first()!!.available)
                        source.snapshot.value = Snapshot(listOf(original.copy(title = "restored")), revision = 3)
                        awaitCommitted(3)
                        assertEquals("restored", audios.getAudio(original.id).first()!!.title)
                        assertTrue(platform.isEnabled(source))
                    }
                }
                transactionEntered.await()
                val disable = async(start = CoroutineStart.UNDISPATCHED) {
                    repository.setSourceEnabled(source.name, false)
                }
                assertFalse(disable.isCompleted)
                assertTrue(platform.isEnabled(source))
                finishTransaction.complete(Unit)
                transaction.await()
                assertTrue(disable.await())
                assertFalse(audios.getAudio(original.id).first()!!.available)

                // Use an acknowledged second operation to prove disable completed before checking rejection.
                assertFailsWith<IllegalStateException> {
                    repository.withEnabledSource(source.name) { error("Must not run") }
                }
                assertFalse(repository.retryCommit(source.name))
                assertFalse(audios.getAudio(original.id).first()!!.available)

                // Re-enable and commit a new revision, ensuring the repository remains usable.
                assertTrue(repository.setSourceEnabled(source.name, true))
                source.snapshot.value = Snapshot(listOf(original.copy(title = "enabled-again")), revision = 5)
                awaitCommitted(5)
                val restored = audios.getAudio(original.id).first()!!
                assertTrue(restored.available)
                assertEquals("enabled-again", restored.title)
            } finally {
                job.cancelAndJoin()
                KVContext.kvMap.clear()
            }
        }
    }
}
