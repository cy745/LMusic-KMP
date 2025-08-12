package com.lalilu.lmedia.util

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LGroupItem
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

fun List<LItem>.flatten(): List<LAudio> {
    return flatMap {
        when (it) {
            is LGroupItem -> it.items
            is LAudio -> listOf(it)
            else -> emptyList()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<List<LItem>>.flatten(): Flow<List<LAudio>> {
    return mapLatest { list -> list.flatten() }
}