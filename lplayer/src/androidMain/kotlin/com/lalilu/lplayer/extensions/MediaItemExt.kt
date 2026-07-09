package com.lalilu.lplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LFolder
import com.lalilu.lmedia.domain.model.LGenre
import android.net.Uri
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.AlbumRepository
import com.lalilu.lmedia.domain.repository.ArtistRepository
import org.koin.mp.KoinPlatform
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

fun LAudio.toMediaItem(): MediaItem {
    val uri = Uri.Builder()
        .scheme("lmusic")
        .path("audio")
        .appendQueryParameter("id", id.encodeURLPathPart())
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
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
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs")
                .setMediaType(MEDIA_TYPE_ARTIST)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LAlbum.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs")
                .setMediaType(MEDIA_TYPE_ALBUM)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LFolder.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs")
                .setMediaType(MEDIA_TYPE_FOLDER_MIXED)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun LGenre.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotBlank() } ?: "Songs")
                .setMediaType(MEDIA_TYPE_FOLDER_MIXED)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()
}

fun Any.toMediaItem(): MediaItem? {
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

    private val audioRepo: AudioRepository get() = KoinPlatform.getKoin().get()
    private val albumRepo: AlbumRepository get() = KoinPlatform.getKoin().get()
    private val artistRepo: ArtistRepository get() = KoinPlatform.getKoin().get()

    private fun resolveType(id: String): String = when {
        id.startsWith(com.lalilu.lmedia.domain.model.LAudio.ID_PREFIX) -> "audio"
        id.startsWith(com.lalilu.lmedia.domain.model.LAlbum.ID_PREFIX) -> "album"
        id.startsWith(com.lalilu.lmedia.domain.model.LArtist.ID_PREFIX) -> "artist"
        id.startsWith(com.lalilu.lmedia.domain.model.LFolder.ID_PREFIX) -> "folder"
        id.startsWith(com.lalilu.lmedia.domain.model.LGenre.ID_PREFIX) -> "genre"
        else -> "unknown"
    }

    suspend fun getItem(mediaId: String): MediaItem? = withContext(Dispatchers.IO) {
        when (resolveType(mediaId)) {
            "audio" -> audioRepo.getAudio(mediaId).first()?.toMediaItem()
            "album" -> albumRepo.getAlbum(mediaId).first()?.toMediaItem()
            "artist" -> artistRepo.getArtist(mediaId).first()?.toMediaItem()
            else -> null
        }
    }

    suspend fun getItems(mediaIds: List<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        val audioIds = mediaIds.filter { it.startsWith(com.lalilu.lmedia.domain.model.LAudio.ID_PREFIX) }
        val albumIds = mediaIds.filter { it.startsWith(com.lalilu.lmedia.domain.model.LAlbum.ID_PREFIX) }
        val artistIds = mediaIds.filter { it.startsWith(com.lalilu.lmedia.domain.model.LArtist.ID_PREFIX) }

        buildList {
            if (audioIds.isNotEmpty()) addAll(audioRepo.getAudios(audioIds).first().mapNotNull { it.toMediaItem() })
            if (albumIds.isNotEmpty()) addAll(albumRepo.getAlbums(albumIds).first().mapNotNull { it.toMediaItem() })
            if (artistIds.isNotEmpty()) addAll(artistRepo.getArtists(artistIds).first().mapNotNull { it.toMediaItem() })
        }
    }

    suspend fun getChildren(parentId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (parentId == ALL_SONGS) {
            return@withContext audioRepo.getAudios().first().mapNotNull { it.toMediaItem() }
        }

        when (resolveType(parentId)) {
            "album" -> {
                audioRepo.getAudios().first()
                    .filter { audio ->
                        audio.metadata.album?.let { albumName ->
                            parentId == "${com.lalilu.lmedia.domain.model.LAlbum.ID_PREFIX}$albumName"
                        } ?: false
                    }
                    .mapNotNull { it.toMediaItem() }
            }
            "artist" -> {
                audioRepo.getAudios().first()
                    .filter { audio ->
                        audio.metadata.artist?.split('/', ';', '、', ',', '，')?.any {
                            parentId == "${com.lalilu.lmedia.domain.model.LArtist.ID_PREFIX}$it"
                        } ?: false
                    }
                    .mapNotNull { it.toMediaItem() }
            }
            else -> emptyList()
        }
    }
}
