package com.lalilu.lmedia.coil

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import coil3.toUri
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.usecase.GetArtistRelatedAudiosUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Fetches cover art for an [LArtist] by trying each related audio's picture.
 * Uses [GetArtistRelatedAudiosUseCase] to resolve the artist's songs,
 * then returns the first successful image.
 */
class LArtistCoverFetcher(
    private val artist: LArtist,
    private val getRelatedAudios: GetArtistRelatedAudiosUseCase,
    private val sourceMap: Map<String, MediaSource>,
    private val imageLoader: ImageLoader,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val audios = getRelatedAudios(artist)
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

class LArtistCoverFetcherFactory : Fetcher.Factory<LArtist>, KoinComponent {
    private val getRelatedAudios by inject<GetArtistRelatedAudiosUseCase>()
    private val platformMediaSource by inject<PlatformMediaSource>()
    private val sourceMap by lazy { platformMediaSource.sources.associateBy { it.name } }

    override fun create(
        data: LArtist,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher? {
        return LArtistCoverFetcher(
            artist = data,
            getRelatedAudios = getRelatedAudios,
            sourceMap = sourceMap,
            imageLoader = imageLoader,
            options = options
        )
    }
}

class LArtistCoverKeyer : Keyer<LArtist> {
    override fun key(data: LArtist, options: Options): String? {
        return "artist_cover_${data.id}"
    }
}
