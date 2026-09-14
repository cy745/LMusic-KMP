package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [PlaybackFailureRepository::class])
class PlaybackFailureRepositoryImpl(database: ILMediaDatabase) : PlaybackFailureRepository {
    private val dao = database.playbackFailureDao()
    override val failures = dao.observeAll().map { rows ->
        rows.associate { row -> row.playbackId to PlaybackFailure(
            PlaybackFailureReason.entries.firstOrNull { it.name == row.reason } ?: PlaybackFailureReason.Unknown,
            row.occurredAtMillis,
        ) }
    }

    override suspend fun record(playbackId: String, failure: PlaybackFailure) {
        require(MediaKey.parse(playbackId) != null)
        dao.record(playbackId, failure.reason.name, failure.occurredAtMillis)
    }

    override suspend fun clearAfterSuccessfulPlayback(playbackId: String) {
        require(MediaKey.parse(playbackId) != null)
        dao.clear(playbackId)
    }
}
