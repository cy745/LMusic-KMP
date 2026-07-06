package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.Flow

interface AudioRepository {
    fun getAudios(): Flow<List<LAudio>>
    fun getAudios(ids: List<String>): Flow<List<LAudio>>
    fun getAudio(id: String): Flow<LAudio?>
}
