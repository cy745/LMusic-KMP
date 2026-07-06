package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.domain.model.LGenre

fun LGenreEntity.toDomain(): LGenre = LGenre(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun LGenre.toEntity(): LGenreEntity = LGenreEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)
