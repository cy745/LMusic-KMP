package com.lalilu.lmedia.coil

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import coil3.toUri
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.usecase.GetAlbumRelatedAudiosUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Fetches cover art for an [LAlbum] by trying each related audio's picture.
 * Uses [GetAlbumRelatedAudiosUseCase] to resolve the album's songs,
 * then returns the first successful image.
 */
class LAlbumCoverFetcher(
    private val album: LAlbum,
    private val getRelatedAudios: GetAlbumRelatedAudiosUseCase,
    private val sourceMap: Map<String, MediaSource>,
    private val imageLoader: ImageLoader,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val audios = getRelatedAudios(album)
        for (audio in audios) {
            val mediaSource = sourceMap[audio.mediaSourceName] ?: continue
            val picture = mediaSource.dataSource?.getPicture(audio) ?: continue
            val data = when (picture) {
                is MediaData.Bytes -> picture.bytes
                is MediaData.Url -> picture.url.toUri()
            }
            val fetcher = imageLoader.components.newFetcher(data, options, imageLoader)
                ?.first ?: continue
            return fetcher.fetch()
        }
        return null
    }
}

class LAlbumCoverFetcherFactory : Fetcher.Factory<LAlbum>, KoinComponent {
    private val getRelatedAudios by inject<GetAlbumRelatedAudiosUseCase>()
    private val platformMediaSource by inject<PlatformMediaSource>()
    private val sourceMap by lazy { platformMediaSource.sources.associateBy { it.name } }

    override fun create(
        data: LAlbum,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher? {
        return LAlbumCoverFetcher(
            album = data,
            getRelatedAudios = getRelatedAudios,
            sourceMap = sourceMap,
            imageLoader = imageLoader,
            options = options
        )
    }
}

class LAlbumCoverKeyer : Keyer<LAlbum> {
    override fun key(data: LAlbum, options: Options): String? {
        return "album_cover_${data.id}"
    }
}
