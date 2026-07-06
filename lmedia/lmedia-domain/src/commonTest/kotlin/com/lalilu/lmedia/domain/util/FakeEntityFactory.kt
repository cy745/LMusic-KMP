package com.lalilu.lmedia.domain.util

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LFolder
import com.lalilu.lmedia.domain.model.LGenre
import com.lalilu.lmedia.domain.model.Metadata

fun createAudio(
    id: String = "test",
    title: String = "Test Song $id",
    subtitle: String = "Test Artist",
    mediaSourceName: String = "test_source",
    metadata: Metadata = Metadata.EMPTY,
    extra: Map<String, String>? = null,
    available: Boolean = true,
): LAudio = LAudio(
    id = "${LAudio.ID_PREFIX}$id",
    title = title,
    subtitle = subtitle,
    mediaSourceName = mediaSourceName,
    metadata = metadata,
    extra = extra,
    available = available
)

fun createAlbum(
    id: String = "test",
    title: String = "Test Album $id",
    subtitle: String = "",
    extra: Map<String, String>? = null,
): LAlbum = LAlbum(
    id = "${LAlbum.ID_PREFIX}$id",
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun createArtist(
    id: String = "test",
    title: String = "Test Artist $id",
    subtitle: String = "",
    extra: Map<String, String>? = null,
): LArtist = LArtist(
    id = "${LArtist.ID_PREFIX}$id",
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun createGenre(
    id: String = "test",
    title: String = "Test Genre $id",
    subtitle: String = "",
    extra: Map<String, String>? = null,
): LGenre = LGenre(
    id = "${LGenre.ID_PREFIX}$id",
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun createFolder(
    id: String = "test",
    title: String = "Test Folder $id",
    subtitle: String = "",
    extra: Map<String, String>? = null,
): LFolder = LFolder(
    id = "${LFolder.ID_PREFIX}$id",
    title = title,
    subtitle = subtitle,
    extra = extra
)
