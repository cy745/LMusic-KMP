package com.lalilu.lmusic.repository

import com.lalilu.lmedia.data.repository.PlaybackFailureRepositoryImpl
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackFailurePersistenceTest {
    @Test fun reopeningDatabaseKeepsFailuresAndSuccessfulRetryClearsOnlyItsOwnSong() = runTest {
        val name = "playback-failure-test-${UUID.randomUUID()}"
        val local = MediaKey("local", "42").stableKey
        val remote = MediaKey("remote", "42").stableKey
        val failure = PlaybackFailure(PlaybackFailureReason.Decode, 123L)
        val network = PlaybackFailure(PlaybackFailureReason.Network, 456L)
        try {
            val original = requireDatabase<LMusicDatabase>(name = name, forceMemory = false)
            try {
                PlaybackFailureRepositoryImpl(original).apply {
                    record(local, failure)
                    record(remote, network)
                }
            } finally {
                original.close()
            }

            val reopened = requireDatabase<LMusicDatabase>(name = name, forceMemory = false)
            try {
                val repository = PlaybackFailureRepositoryImpl(reopened)
                assertEquals(mapOf(local to failure, remote to network), repository.failures.first())
                repository.clearAfterSuccessfulPlayback(local)
            } finally {
                reopened.close()
            }

            val reopenedAgain = requireDatabase<LMusicDatabase>(name = name, forceMemory = false)
            try {
                assertEquals(mapOf(remote to network), PlaybackFailureRepositoryImpl(reopenedAgain).failures.first())
            } finally {
                reopenedAgain.close()
            }
        } finally {
            // Only remove this test's uniquely named database files, never the app database.
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                Files.deleteIfExists(Paths.get("db/$name.db$suffix"))
            }
        }
    }
}
