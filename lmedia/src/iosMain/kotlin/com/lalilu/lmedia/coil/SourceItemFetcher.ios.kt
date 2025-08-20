package com.lalilu.lmedia.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import com.lalilu.lmedia.entity.SourceItem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import okio.Buffer
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SourceItemFetcherFactory : SourceItemFetcher {
    actual override fun create(
        data: SourceItem,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher? {
        return super.create(data, options, imageLoader) ?: when (data) {
            is SourceItem.MusicKitItem -> MusicKitItemFetcher(data, options)
            else -> throw IllegalArgumentException("Unsupported data type: ${data::class.simpleName}")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
class MusicKitItemFetcher(
    private val fileItem: SourceItem.MusicKitItem,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val artwork = fileItem.item.artwork()
            ?: throw IllegalArgumentException("No artwork")

        val width = options.size.width.pxOrElse { 0 }.toLong()
        val height = options.size.height.pxOrElse { 0 }.toLong()

        val bytes = artwork.urlWithWidth(width, height)
            ?.let { NSData.dataWithContentsOfURL(it) }
            ?.let { it.bytes?.readBytes(it.length.toInt()) }
            ?: throw IllegalArgumentException("No bytes")

        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                fileSystem = options.fileSystem
            ),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }
}