package com.lalilu.lplayer.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.source.MediaData
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@SuppressLint("UnsafeOptInUsageError")
class LMediaDataSource(
    context: Context,
    private val platformMediaSource: PlatformMediaSource,
    private val defaultDataSource: DefaultDataSource = DefaultDataSource(context, true)
) : DataSource by defaultDataSource {

    class Factory(
        private val context: Context,
        private val platformMediaSource: PlatformMediaSource,
    ) : DataSource.Factory {

        override fun createDataSource(): DataSource {
            return LMediaDataSource(
                context = context,
                platformMediaSource = platformMediaSource
            )
        }
    }

    private var byteDataSource: ByteArrayDataSource? = null
    private var readingUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        readingUri = dataSpec.uri

        if (dataSpec.uri.scheme != "lmusic") {
            return defaultDataSource.open(dataSpec)
        }

        val id = uri?.getQueryParameter("id")
            ?.decodeURLPart()
        val item = LMedia.instance.get<LAudio>(id)
            ?: return defaultDataSource.open(dataSpec)

        val source = platformMediaSource.sources
            .firstOrNull { item.mediaSourceName == it.name }
            ?: throw Exception("No source item found for ${item.mediaSourceName}")

        val data = runBlocking(Dispatchers.io) {
            source.dataSource.getMedia(item)
        }

        return when (data) {
            is MediaData.Bytes -> {
                ByteArrayDataSource(data.bytes)
                    .also { byteDataSource = it }
                    .open(dataSpec)
            }

            is MediaData.Url -> {
                val newSpec = DataSpec.Builder()
                    .setUri(data.url.toUri())
                    .setKey(dataSpec.key)
                    .setCustomData(dataSpec.customData)
                    .setLength(dataSpec.length)
                    .setFlags(dataSpec.flags)
                    .setHttpBody(dataSpec.httpBody)
                    .setHttpMethod(dataSpec.httpMethod)
                    .setPosition(dataSpec.position)
                    .setHttpRequestHeaders(dataSpec.httpRequestHeaders)
                    .setUriPositionOffset(dataSpec.uriPositionOffset)
                    .build()
                readingUri = newSpec.uri
                defaultDataSource.open(newSpec)
            }

            else -> defaultDataSource.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        byteDataSource?.let { return it.read(buffer, offset, length) }
        return defaultDataSource.read(buffer, offset, length)
    }

    override fun close() {
        byteDataSource?.let { return it.close() }
        return defaultDataSource.close()
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return super.getResponseHeaders()
    }

    override fun getUri(): Uri? = readingUri
}

