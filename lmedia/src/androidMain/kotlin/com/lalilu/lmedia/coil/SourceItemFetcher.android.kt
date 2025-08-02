package com.lalilu.lmedia.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.SourceItem
import okio.buffer
import okio.source
import java.io.FileNotFoundException

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SourceItemFetcherFactory : SourceItemFetcher {

    actual override fun create(
        data: SourceItem,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher? {
        return super.create(data, options, imageLoader) ?: when (data) {
            is SourceItem.FileItem -> FileSourceItemFetcher(data, options)
            else -> throw IllegalArgumentException("Unsupported data type: ${data::class.simpleName}")
        }
    }
}

class FileSourceItemFetcher(
    private val fileItem: SourceItem.FileItem,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val file = fileItem.file
        if (!file.exists()) throw FileNotFoundException("File not found: ${file.path}")
        if (!file.canRead()) throw FileNotFoundException("File not readable: ${file.path}")

        val path = file.path
        if (path.isBlank()) throw IllegalArgumentException("Invalid path: $path")

        val stream = Taglib.getPicture(path = path)?.inputStream()
            ?: throw FileNotFoundException("Not found picture for $path")

        return SourceFetchResult(
            source = ImageSource(stream.source().buffer(), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }
}