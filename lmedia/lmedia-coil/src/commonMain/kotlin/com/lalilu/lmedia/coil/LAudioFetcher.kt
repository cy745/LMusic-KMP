package com.lalilu.lmedia.coil

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toUri
import com.lalilu.lmedia.MediaCoverRequest
import com.lalilu.lmedia.domain.debug.DebugSwitches
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.*
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = co.touchlab.kermit.Logger.withTag("LAudioFetcher")

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

        // ── 调试埋点：封面这条链是「切歌闪底色」的嫌疑区 ──
        // 把每次解析的输入与结果打出来（logcat 自带毫秒时间戳，用不着自己计时）。
        // 只在调试通道打开时输出，正式包零噪音、零分支开销。
        if (DebugSwitches.enabled) {
            logger.i {
                "DebugCover: start audio=${audio.id} title='${audio.title}' " +
                    "size=${options.size.width}x${options.size.height} extra=${audio.extra?.keys?.toList()}"
            }
        }
        if (DebugSwitches.fakeCoverDelayMs > 0L) {
            delay(DebugSwitches.fakeCoverDelayMs)
        }
        if (DebugSwitches.fakeCoverFail) {
            logger.w { "DebugCover: fakeCoverFail=true -> 强制返回 null audio=${audio.id}" }
            return null
        }

        // Coil 3 Size.width/height 是 Dimension 类型，
        // pxOrElse { 0 } 提取像素值，未指定时回退 0
        val fetchOptions = MediaFetchOptions(
            width = options.size.width.pxOrElse { 0 },
            height = options.size.height.pxOrElse { 0 },
        )
        val pictureData = resolvePicture(audio, fetchOptions)
        if (DebugSwitches.enabled) {
            logger.i { "DebugCover: result=${pictureData ?: "null"} audio=${audio.id}" }
        }
        if (pictureData == null) return null

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
    private val sourceMap by lazy { platformMediaSource.sources.associateBy { it.name } }

    override fun key(data: LAudio, options: Options): String? {
        val generation = sourceMap[data.mediaSourceName]
            ?.contentState
            ?.value
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
