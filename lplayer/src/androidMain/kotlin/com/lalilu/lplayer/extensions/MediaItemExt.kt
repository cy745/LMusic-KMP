package com.lalilu.lplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LFolder
import com.lalilu.lmedia.entity.LGenre
import com.lalilu.lmedia.entity.ref
import android.net.Uri
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.refCount
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun LAudio.toMediaItem(): MediaItem {
    val uri = Uri.Builder()
        .scheme("lmusic")
        .path("audio")
        .appendQueryParameter("id", id.encodeURLPathPart())
        .build()

    return MediaItem.Builder()
        .setMediaId(idValue())
        .setUri(uri)
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
        .setMediaId(idValue())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs: ${refCount<LAudio>()}")
                .setMediaType(MEDIA_TYPE_ARTIST)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LAlbum.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(idValue())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs: ${refCount<LAudio>()}")
                .setMediaType(MEDIA_TYPE_ALBUM)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LFolder.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(idValue())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs: ${refCount<LAudio>()}")
                .setMediaType(MEDIA_TYPE_FOLDER_MIXED)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LGenre.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(idValue())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs: ${refCount<LAudio>()}")
                .setMediaType(MEDIA_TYPE_FOLDER_MIXED)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LItem.toMediaItem(): MediaItem? {
    return when (this) {
        is LAudio -> toMediaItem()
        is LArtist -> toMediaItem()
        is LAlbum -> toMediaItem()
        is LGenre -> toMediaItem()
        is LFolder -> toMediaItem()
        else -> null
    }
}

object MMedia {
    const val ROOT = "root"
    const val ALL_SONGS = "all_songs"
    const val ALL_ARTISTS = "all_artists"
    const val ALL_ALBUMS = "all_albums"

    suspend fun getItem(mediaId: String): MediaItem? = withContext(Dispatchers.IO) {
        LMedia.instance.getByPrefix(mediaId)?.toMediaItem()
    }

    suspend fun getItems(mediaIds: List<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        LMedia.instance.mapByByPrefix(mediaIds).mapNotNull { it.toMediaItem() }
    }

    suspend fun getChildren(parentId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (parentId == ALL_SONGS) return@withContext LMedia.instance.get<LAlbum>().map { it.toMediaItem() }

        LMedia.instance.getByPrefix(parentId)
            ?.ref<LAudio>()
            ?.map { it.toMediaItem() }
            ?: emptyList()
    }
}
