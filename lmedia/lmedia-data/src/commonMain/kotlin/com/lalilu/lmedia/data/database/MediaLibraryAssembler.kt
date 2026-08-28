package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXGenre
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.data.mapper.toEntity
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.albumArtist
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistNames
import com.lalilu.lmedia.domain.model.genre
import com.lalilu.lmedia.domain.model.libraryAlbumId
import com.lalilu.lmedia.domain.model.libraryArtistIds
import com.lalilu.lmedia.domain.model.libraryGenreId
import com.lalilu.lmedia.domain.model.normalizeLibraryName

internal data class MediaLibraryBatch(
    val audios: List<LAudioEntity>,
    val artists: List<LArtistEntity>,
    val albums: List<LAlbumEntity>,
    val genres: List<LGenreEntity>,
    val artistRelations: List<CrossRefLAudioXLArtist>,
    val albumRelations: List<CrossRefLAudioXAlbum>,
    val genreRelations: List<CrossRefLAudioXGenre>,
)

/** 把单个数据源的完整歌曲结果转换为数据库实体和强类型关系。 */
internal object MediaLibraryAssembler {
    fun assemble(audios: List<LAudio>): MediaLibraryBatch {
        val sourceAudios = audios.distinctBy(LAudio::id)
        val artists = linkedMapOf<String, LArtistEntity>()
        val albums = linkedMapOf<String, LAlbumEntity>()
        val genres = linkedMapOf<String, LGenreEntity>()
        val artistRelations = mutableListOf<CrossRefLAudioXLArtist>()
        val albumRelations = mutableListOf<CrossRefLAudioXAlbum>()
        val genreRelations = mutableListOf<CrossRefLAudioXGenre>()

        sourceAudios.forEach { audio ->
            audio.artistNames().zip(audio.libraryArtistIds()).forEach { (rawName, artistId) ->
                val name = normalizeLibraryName(rawName)
                artists.getOrPut(artistId) {
                    LArtistEntity(id = artistId, title = name, subtitle = "")
                }
                artistRelations += CrossRefLAudioXLArtist(artistId = artistId, songId = audio.id)
            }

            val albumName = normalizeLibraryName(audio.albumName ?: "Unknown")
            val albumArtist = audio.albumArtist?.let(::normalizeLibraryName).orEmpty()
            val albumId = audio.libraryAlbumId()
            albums.getOrPut(albumId) {
                LAlbumEntity(id = albumId, title = albumName, subtitle = albumArtist)
            }
            albumRelations += CrossRefLAudioXAlbum(albumId = albumId, songId = audio.id)

            audio.libraryGenreId()?.let { genreId ->
                val genreName = normalizeLibraryName(audio.genre.orEmpty())
                genres.getOrPut(genreId) {
                    LGenreEntity(id = genreId, title = genreName, subtitle = "")
                }
                genreRelations += CrossRefLAudioXGenre(genreId = genreId, songId = audio.id)
            }
        }

        return MediaLibraryBatch(
            audios = sourceAudios.map { it.copy(available = true).toEntity() },
            artists = artists.values.toList(),
            albums = albums.values.toList(),
            genres = genres.values.toList(),
            artistRelations = artistRelations.distinct(),
            albumRelations = albumRelations.distinct(),
            genreRelations = genreRelations.distinct(),
        )
    }
}
