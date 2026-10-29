package com.lalilu.lmedia.coil

import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toUri
import com.lalilu.lmedia.MediaCoverRequest
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val coverLogger = Logger.withTag("CoverFetch")

class LAudioFetcher(
    val audio: LAudio,
    val options: Options,
    val imageLoader: ImageLoader,
    val resolvePicture: suspend (LAudio, MediaFetchOptions) -> MediaData?,
) : Fetcher {
    private var actualFetcher: Fetcher? = null

    override suspend fun fetch(): FetchResult? {
        val fetcher = actualFetcher
        if (fetcher != null) {
            return fetcher.fetch()
        }

        // Coil 3 Size.width/height 是 Dimension 类型，
        // pxOrElse { 0 } 提取像素值，未指定时回退 0
        val fetchOptions = MediaFetchOptions(
            width = options.size.width.pxOrElse { 0 },
            height = options.size.height.pxOrElse { 0 },
        )
        // 等它变可用，而不是一次落空就返回 null：null 在 Coil 里表示"我不认识这份数据"，
        // 会让整次请求以"没有 fetcher 支持"结束（封面就再也不重试了）。
        val pictureData = awaitCover { resolvePicture(audio, fetchOptions) }
        if (pictureData == null) {
            coverLogger.w { "封面等待超时：${audio.mediaSourceName}/${audio.id}（${audio.title}）" }
            throw CoverUnavailableException(audio.mediaSourceName, audio.id)
        }
        coverLogger.i {
            "封面就绪：${audio.mediaSourceName}/${audio.id}（${audio.title}，${pictureData::class.simpleName}）"
        }

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

    override fun create(
        data: LAudio,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher {
        return LAudioFetcher(
            audio = data,
            options = options,
            imageLoader = imageLoader,
            resolvePicture = { audio, fetchOptions ->
                platformMediaSource.resolvePictureData(
                    audio = audio,
                    options = fetchOptions,
                    timeoutMillis = 5_000L,
                )
            },
        )
    }
}

class LAudioKeyer : Keyer<LAudio>, KoinComponent {
    private val platformMediaSource by inject<PlatformMediaSource>()

    override fun key(data: LAudio, options: Options): String? {
        val generation = platformMediaSource.findSource(data.mediaSourceName)
            ?.contentState
            ?.value
            ?.forItem(data.id)
            ?.generation
            ?: 0L
        return mediaCoverCacheKey(data, generation)
    }
}

class MediaCoverRequestFetcherFactory : Fetcher.Factory<MediaCoverRequest>, KoinComponent {
    private val platformMediaSource by inject<PlatformMediaSource>()

    override fun create(
        data: MediaCoverRequest,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = LAudioFetcher(
        audio = data.audio,
        options = options,
        imageLoader = imageLoader,
        resolvePicture = { audio, fetchOptions ->
            platformMediaSource.resolvePictureData(
                audio = audio,
                options = fetchOptions,
                timeoutMillis = 5_000L,
            )
        },
    )
}

class MediaCoverRequestKeyer : Keyer<MediaCoverRequest> {
    override fun key(data: MediaCoverRequest, options: Options): String =
        mediaCoverCacheKey(data.audio, data.generation)
}

private fun mediaCoverCacheKey(audio: LAudio, generation: Long): String =
    "${audio.mediaSourceName}_${audio.id}_$generation"

/** 封面暂不可用：让 Coil 正常结束请求；日志端可省略这类预期错误的昂贵原生堆栈。 */
class CoverUnavailableException(sourceName: String, audioId: String) :
    IllegalStateException("封面暂时取不到：$sourceName/$audioId")
