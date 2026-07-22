package com.lalilu.lmedia.coil

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import coil3.toUri
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.first

class LAudioFetcher(
    val audio: LAudio,
    val options: Options,
    val imageLoader: ImageLoader,
    val source: (String) -> MediaSource?
) : Fetcher {
    private var actualFetcher: Fetcher? = null

    override suspend fun fetch(): FetchResult? {
        val fetcher = actualFetcher
        if (fetcher != null) {
            return fetcher.fetch()
        }

        val source = source(audio.mediaSourceName)
            ?.dataSource
            ?: throw IllegalArgumentException("MediaSource not found")

        val pictureData = source.getPicture(audio) ?: return null

        val data = when (pictureData) {
            is MediaData.Bytes -> pictureData.bytes
            is MediaData.Url -> pictureData.url.toUri()
        }

        actualFetcher = imageLoader.components.newFetcher(
            data = data,
            options = options,
            imageLoader = imageLoader
        )?.first
            ?: throw IllegalArgumentException("Fetcher not found for data: $data")

        return actualFetcher?.fetch()
    }
}

class LAudioFetcherFactory : Fetcher.Factory<LAudio>, KoinComponent {
    private val platformMediaSource by inject<PlatformMediaSource>()
    private val sourceMap by lazy { platformMediaSource.sources.associateBy { it.name } }

    override fun create(
        data: LAudio,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher {
        return LAudioFetcher(
            audio = data,
            options = options,
            imageLoader = imageLoader,
            source = { name -> sourceMap[name] }
        )
    }
}

class LAudioKeyer : Keyer<LAudio> {
    override fun key(data: LAudio, options: Options): String? {
        return "${data.mediaSourceName}_${data.id}"
    }
}