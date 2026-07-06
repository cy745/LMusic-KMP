package com.lalilu.lmedia.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over KV storage for daily recommend IDs.
 * Implementation is provided by the consumer module (lhome).
 */
interface DailyRecommendsStorage {
    fun flow(): Flow<List<String>>
    suspend fun set(ids: List<String>)
}
