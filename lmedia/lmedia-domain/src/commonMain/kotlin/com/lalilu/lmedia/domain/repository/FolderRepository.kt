package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LFolder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getFolders(): Flow<List<LFolder>>
    fun getFolder(id: String): Flow<LFolder?>
}
