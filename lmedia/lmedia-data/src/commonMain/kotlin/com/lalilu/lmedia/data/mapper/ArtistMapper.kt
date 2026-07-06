package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.domain.model.LArtist

fun LArtistEntity.toDomain(): LArtist = LArtist(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun LArtist.toEntity(): LArtistEntity = LArtistEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)
