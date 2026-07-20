package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.data.mapper.toDomain
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [AudioRepository::class])
class AudioRepositoryImpl(
    private val database: ILMediaDatabase
) : AudioRepository {
    private val audioDao by lazy { database.audioDao() }

    override fun getAudios(): Flow<List<LAudio>> =
        audioDao.getAllAudio().mapLatest { list -> list.map { it.toDomain() } }

    override fun getAudios(ids: List<String>): Flow<List<LAudio>> =
        audioDao.getAudios(ids).mapLatest { list -> list.map { it.toDomain() } }

    override fun getAudio(id: String): Flow<LAudio?> =
        audioDao.getAudio(id).mapLatest { it?.toDomain() }

    override suspend fun clearUnavailableAudio() {
        audioDao.clearUnavailableAudio()
    }
}
