package com.lalilu.lmedia.coil

import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.lalilu.lmedia.entity.Remote
import com.lalilu.lmedia.source.RemoteSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import okio.Buffer
import org.koin.core.component.KoinComponent

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteDataFetcher(
    private val remoteData: Remote,
    private val options: Options
) : Fetcher, KoinComponent {
    val remoteSource
        get() = getKoin().getOrNull<RemoteSource>()

    override suspend fun fetch(): FetchResult? {
        val remoteServer = remoteSource
        if (remoteServer == null) {
            throw IllegalStateException("[${remoteData.type}: ${remoteData.id}]: Remote server not found")
        }

        val data = remoteServer.remoteServiceFlow
            .flatMapLatest { service ->
                service?.requirePictureFlow(remoteData.id, remoteData.type)
                    ?: flowOf(null)
            }.firstOrNull()

        if (data == null) {
            throw Exception("[${remoteData.type}: ${remoteData.id}]: Data is null")
        }

        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(data) },
                fileSystem = options.fileSystem,
            ),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }
}