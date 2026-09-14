package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.repository.PlaybackFailureRepositoryImpl
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackFailureDatabaseTest {
    @Test fun failuresAreSourceQualifiedSurviveMetadataUpdatesAndClearIndependently() = runTest {
        val db = requireDatabase<LMusicDatabase>(forceMemory = true)
        try {
            val repository = PlaybackFailureRepositoryImpl(db)
            val local = MediaKey("local", "42").stableKey
            val remote = MediaKey("remote", "42").stableKey
            val failure = PlaybackFailure(PlaybackFailureReason.Decode, 123L)
            repository.record(local, failure)
            repository.record(remote, failure.copy(reason = PlaybackFailureReason.Network))
            val audio = LAudioEntity(id = "42", mediaSourceName = "local")
            db.audioDao().insert(audio)
            db.audioDao().update(audio.copy(title = "rescanned", available = true))
            assertEquals(failure, repository.failures.first()[local])
            repository.clearAfterSuccessfulPlayback(local)
            assertEquals(setOf(remote), repository.failures.first().keys)
            repository.clearAfterSuccessfulPlayback(remote)
            assertTrue(repository.failures.first().isEmpty())
        } finally {
            db.close()
        }
    }
}
