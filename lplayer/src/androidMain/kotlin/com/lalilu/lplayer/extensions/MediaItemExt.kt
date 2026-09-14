package com.lalilu.lplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.domain.repository.getPlaybackSlots
import com.lalilu.lmedia.domain.model.LFolder
import com.lalilu.lmedia.domain.model.LGenre
import com.lalilu.lmedia.domain.model.albumArtist
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.model.duration
import com.lalilu.lmedia.domain.model.libraryAlbumId
import com.lalilu.lmedia.domain.model.libraryArtistIds
import android.net.Uri
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.AlbumRepository
import com.lalilu.lmedia.domain.repository.ArtistRepository
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

fun LAudio.toMediaItem(): MediaItem {
    val uri = Uri.Builder()
        .scheme("lmusic")
        .path("audio")
        .appendQueryParameter("playbackId", playbackId)
        .build()

    return MediaItem.Builder()
        .setMediaId(playbackId)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(subtitle)
                .setArtist(artistName)
                .setSubtitle(artistName)
                .setAlbumTitle(albumName)
                .setAlbumArtist(albumArtist)
                .setDurationMs(duration)
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
        if (MediaKey.parse(mediaId) != null) {
            return@withContext audioRepo.getAudioByPlaybackId(mediaId).first()?.toMediaItem()
        }
        when (resolveType(mediaId)) {
            "album" -> albumRepo.getAlbum(mediaId).first()?.toMediaItem()
            "artist" -> artistRepo.getArtist(mediaId).first()?.toMediaItem()
            else -> null
        }
    }

    suspend fun getItems(mediaIds: List<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (mediaIds.all { MediaKey.parse(it) != null }) {
            return@withContext audioRepo.getPlaybackSlots(mediaIds).first().map { audio ->
                requireNotNull(audio) { "Requested playback item is unavailable" }.toMediaItem()
            }
        }
        mediaIds.map { id ->
            requireNotNull(getItem(id)) { "Requested media item is unavailable" }
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
                        parentId == audio.libraryAlbumId()
                    }
                    .mapNotNull { it.toMediaItem() }
            }
            "artist" -> {
                audioRepo.getAudios().first()
                    .filter { audio ->
                        parentId in audio.libraryArtistIds()
                    }
                    .mapNotNull { it.toMediaItem() }
            }
            else -> emptyList()
        }
    }
}
