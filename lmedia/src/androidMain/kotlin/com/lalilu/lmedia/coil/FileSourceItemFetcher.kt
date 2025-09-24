package com.lalilu.lmedia.coil

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