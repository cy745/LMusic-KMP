package com.lalilu.lmedia.coil

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import coil3.toUri
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.source.MediaData
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LAudioFetcher(
    val audio: LAudio,
    val options: Options
) : Fetcher, KoinComponent {
    private val platformMediaSource by inject<PlatformMediaSource>()
    private val imageLoader by lazy { SingletonImageLoader.get(options.context) }
    private var actualFetcher: Fetcher? = null

    override suspend fun fetch(): FetchResult? {
        val fetcher = actualFetcher
        if (fetcher != null) {
            return fetcher.fetch()
        }

        val source = platformMediaSource.sources
            .firstOrNull { it.name == audio.mediaSourceName }
            ?.dataSource
            ?: throw IllegalArgumentException("MediaSource not found")

        val pictureData = source.getPicture(audio)
            ?: throw IllegalArgumentException("Picture not found for data: $audio")

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

class LAudioFetcherFactory() : Fetcher.Factory<LAudio> {
    override fun create(
        data: LAudio,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher? {
        return LAudioFetcher(data, options)
    }
}

class LAudioKeyer : Keyer<LAudio> {
    override fun key(data: LAudio, options: Options): String? {
        return "${data.mediaSourceName}_${data.id}"
    }
}