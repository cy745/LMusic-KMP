package com.lalilu.lmedia.entity

import com.lalilu.lmedia.domain.model.LAudio as DomainAudio
import com.lalilu.lmedia.domain.model.LAlbum as DomainAlbum
import com.lalilu.lmedia.domain.model.LArtist as DomainArtist
import com.lalilu.lmedia.domain.model.LItem as DomainLItem
import com.lalilu.lmedia.domain.model.LGenre as DomainGenre
import com.lalilu.lmedia.domain.model.LFolder as DomainFolder
import com.lalilu.lmedia.domain.model.Metadata as DomainMetadata

fun DomainAudio.toLegacyAudio(): LAudio = LAudio(
    id = id,
    title = title,
    subtitle = subtitle,
    mediaSourceName = mediaSourceName,
    metadata = com.lalilu.lmedia.entity.Metadata(
        title = metadata.title,
        album = metadata.album,
        artist = metadata.artist,
        albumArtist = metadata.albumArtist,
        composer = metadata.composer,
        lyricist = metadata.lyricist,
        comment = metadata.comment,
        genre = metadata.genre,
        track = metadata.track,
        disc = metadata.disc,
        date = metadata.date,
        duration = metadata.duration,
        dateAdded = metadata.dateAdded,
        dateModified = metadata.dateModified
    ),
    extra = extra,
    available = available
)

fun DomainAlbum.toLegacyAlbum(): LAlbum = LAlbum(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun DomainArtist.toLegacyArtist(): LArtist = LArtist(
    id = id,
    title = title,
    subtitle = subtitle,
    extra = extra
)

fun DomainLItem.toLegacyLItem(): LItem = when (this) {
    is DomainAudio -> toLegacyAudio()
    is DomainAlbum -> toLegacyAlbum()
    is DomainArtist -> toLegacyArtist()
    is DomainGenre -> LGenre(id = id, title = titleValue(), subtitle = subtitleValue(), extra = extraValue())
    is DomainFolder -> LFolder(id = id, title = titleValue(), subtitle = subtitleValue(), extra = extraValue())
    else -> error("Unknown LItem type: $this")
}
