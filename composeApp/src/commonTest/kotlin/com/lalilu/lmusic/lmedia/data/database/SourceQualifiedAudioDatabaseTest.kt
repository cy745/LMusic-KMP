package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.repository.getPlaybackSlots
import com.lalilu.lmedia.data.repository.AudioRepositoryImpl
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercise real snapshot transactions, not a repository fake that already supports collisions. */
class SourceQualifiedAudioDatabaseTest {
    @Test
    fun identicalRawIdsFromDifferentSourcesSurviveIndependentScansAndCleanup() = runTest {
        val database = requireDatabase<LMusicDatabase>(forceMemory = true)
        try {
            val local = LAudio(id = "42", mediaSourceName = "local", title = "Local song")
            val remote = LAudio(id = "42", mediaSourceName = "remote", title = "Remote song")
            val media = database.mediaDao()
            media.insert(Snapshot(audios = listOf(local)), "local")
            media.insert(Snapshot(audios = listOf(remote)), "remote")

            val rows = database.audioDao().getAllAudio().first()
            assertEquals(2, rows.size)
            assertEquals(setOf("local", "remote"), rows.map { it.mediaSourceName }.toSet())
            assertTrue(rows.all { it.id == "42" && it.available })
            assertEquals(setOf(local.playbackId, remote.playbackId), rows.map { it.playbackId }.toSet())
            assertEquals(2, database.albumDao().getAllAlbumWithAudios().first().single().audios.size)
            val repository = AudioRepositoryImpl(database, PlatformMediaSource(emptyList()))
            assertEquals(
                listOf("Remote song", "Local song", "Remote song"),
                repository.getPlaybackSlots(listOf(remote.playbackId, local.playbackId, remote.playbackId))
                    .first().map { it?.title },
            )

            media.insert(Snapshot(audios = emptyList()), "remote")
            assertTrue(media.getAudioBySource("local").single().available)
            assertEquals(false, media.getAudioBySource("remote").single().available)

            media.clearUnavailableMedia(activeSourceNames = listOf("local", "remote"))
            assertEquals(listOf("local"), database.audioDao().getAllAudio().first().map { it.mediaSourceName })
            assertEquals("Local song", media.getAudioBySource("local").single().title)
            assertEquals(listOf(local.playbackId), database.albumDao().getAllAlbumWithAudios().first().single().audios.map { it.playbackId })
        } finally {
            database.close()
        }
    }
}
