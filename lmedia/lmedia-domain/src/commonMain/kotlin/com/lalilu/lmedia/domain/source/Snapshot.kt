package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 某一个数据源最近一次完整、成功的歌曲结果。
 *
 * 运行状态由 [MediaSource.state] 独立表达；歌手、专辑、流派及关系由数据库写入层从 [audios]
 * 统一重组，因此 Snapshot 不再携带派生实体和临时 Loading/Error 状态。
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class Snapshot(
    val audios: List<LAudio> = emptyList(),
    val revision: Long = 0L,
    val updateTime: Long = Clock.System.now().toEpochMilliseconds(),
)
