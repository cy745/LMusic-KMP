package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.LFolderDao
import com.lalilu.lmedia.data.mapper.toDomain
import com.lalilu.lmedia.domain.model.LFolder
import com.lalilu.lmedia.domain.repository.FolderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [FolderRepository::class])
class FolderRepositoryImpl(
    private val folderDao: LFolderDao
) : FolderRepository {

    override fun getFolders(): Flow<List<LFolder>> =
        folderDao.getAllFolder().mapLatest { list -> list.map { it.toDomain() } }

    override fun getFolder(id: String): Flow<LFolder?> =
        folderDao.getFolder(id).mapLatest { it?.toDomain() }
}
