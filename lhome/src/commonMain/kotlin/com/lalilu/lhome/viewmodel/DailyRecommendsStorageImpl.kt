package com.lalilu.lhome.viewmodel

import com.lalilu.lhome.LHomeKV
import com.lalilu.lmedia.domain.repository.DailyRecommendsStorage
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single(binds = [DailyRecommendsStorage::class])
class DailyRecommendsStorageImpl(
    private val lHomeKV: LHomeKV
) : DailyRecommendsStorage {
    override fun flow(): Flow<List<String>> = lHomeKV.dailyRecommends.flow()

    override suspend fun set(ids: List<String>) {
        lHomeKV.dailyRecommends.setData(ids)
    }
}
