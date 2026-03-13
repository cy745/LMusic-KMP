package com.lalilu.lmedia.util

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.ref
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

fun List<LItem>.flatten(): List<LAudio> {
    return flatMap { it.ref<LAudio>() }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<List<LItem>>.flatten(): Flow<List<LAudio>> {
    return mapLatest { list -> list.flatten() }
}
