package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LFolderEntity
import com.lalilu.lmedia.domain.model.LFolder

fun LFolderEntity.toDomain(): LFolder = LFolder(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun LFolder.toEntity(): LFolderEntity = LFolderEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)
