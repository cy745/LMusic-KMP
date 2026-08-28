package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.Flow

interface AudioRepository {
    fun getAudios(): Flow<List<LAudio>>
    fun getAudios(ids: List<String>): Flow<List<LAudio>>
    fun getAudio(id: String): Flow<LAudio?>

    /** 删除不可用歌曲，并清理失去全部歌曲引用的歌手、专辑、流派及关联关系。 */
    suspend fun clearUnavailableAudio()
}
