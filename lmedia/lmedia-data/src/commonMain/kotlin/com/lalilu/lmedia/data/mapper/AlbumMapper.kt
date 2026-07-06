package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.domain.model.LAlbum

fun LAlbumEntity.toDomain(): LAlbum = LAlbum(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun LAlbum.toEntity(): LAlbumEntity = LAlbumEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)
