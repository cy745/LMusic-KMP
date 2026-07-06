package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.domain.model.LAudio

fun LAudioEntity.toDomain(): LAudio = LAudio(
    id = id,
    title = title,
    subtitle = subtitle,
    mediaSourceName = mediaSourceName,
    metadata = metadata,
    extra = extra,
    available = available
)

fun LAudio.toEntity(): LAudioEntity = LAudioEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    mediaSourceName = mediaSourceName,
    metadata = metadata,
    extra = extra,
    available = available
)
