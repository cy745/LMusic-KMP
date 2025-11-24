package com.lalilu.lplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LFolder
import com.lalilu.lmedia.entity.LGenre

fun LAudio.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(subtitle)
                .setArtist(metadata.artist)
                .setSubtitle(metadata.artist)
                .setAlbumTitle(metadata.album)
                .setAlbumArtist(metadata.albumArtist)
                .setDurationMs(metadata.duration)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}


fun LArtist.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId("${MMedia.ARTIST_PREFIX}$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs: ${items.size}")
                .setMediaType(MEDIA_TYPE_ARTIST)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LAlbum.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId("${MMedia.ALBUM_PREFIX}$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs: ${items.size}")
                .setMediaType(MEDIA_TYPE_ALBUM)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LFolder.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId("${MMedia.FOLDER_PREFIX}$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs: ${items.size}")
                .setMediaType(MEDIA_TYPE_FOLDER_MIXED)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LGenre.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId("${MMedia.GENRE_PREFIX}$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs: ${items.size}")
                .setMediaType(MEDIA_TYPE_FOLDER_MIXED)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

object MMedia {
    const val ARTIST_PREFIX = "artist_"
    const val ALBUM_PREFIX = "album_"
    const val GENRE_PREFIX = "genre_"
    const val FOLDER_PREFIX = "folder_"

    const val ROOT = "root"
    const val ALL_SONGS = "all_songs"
    const val ALL_ARTISTS = "all_artists"
    const val ALL_ALBUMS = "all_albums"

    fun getItem(mediaId: String): MediaItem? = LMedia.instance.run {
        when {
            mediaId.startsWith(ARTIST_PREFIX) -> get<LArtist>(mediaId)?.toMediaItem()
            mediaId.startsWith(ALBUM_PREFIX) -> get<LAlbum>(mediaId)?.toMediaItem()
            mediaId.startsWith(GENRE_PREFIX) -> get<LGenre>(mediaId)?.toMediaItem()
            mediaId.startsWith(FOLDER_PREFIX) -> get<LFolder>(mediaId)?.toMediaItem()
            else -> get<LAudio>(mediaId)?.toMediaItem()
        }
    }

    fun mapItems(mediaIds: List<String>): List<MediaItem> {
        return mediaIds.mapNotNull { getItem(mediaId = it) }
    }

    fun getChildren(parentId: String): List<MediaItem> = LMedia.instance.run {
        when {
            parentId == "all_songs" -> {
                get<LAudio>().map { it.toMediaItem() }
            }

            parentId.startsWith(ARTIST_PREFIX) -> {
                val mediaId = parentId.substring(ARTIST_PREFIX.length)
                get<LArtist>(mediaId)?.items?.map { it.toMediaItem() }
            }

            parentId.startsWith(ALBUM_PREFIX) -> {
                val mediaId = parentId.substring(ALBUM_PREFIX.length)
                get<LAlbum>(mediaId)?.items?.map { it.toMediaItem() }
            }

            parentId.startsWith(GENRE_PREFIX) -> {
                val mediaId = parentId.substring(GENRE_PREFIX.length)
                get<LGenre>(mediaId)?.items?.map { it.toMediaItem() }
            }

            parentId.startsWith(FOLDER_PREFIX) -> {
                val mediaId = parentId.substring(FOLDER_PREFIX.length)
                get<LFolder>(mediaId)?.items?.map { it.toMediaItem() }
            }

            else -> emptyList()
        } ?: emptyList()
    }
}
