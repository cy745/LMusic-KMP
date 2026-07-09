package com.lalilu.lmedia.coil

import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import com.lalilu.lmedia.source.SourceItem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import okio.Buffer
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL

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