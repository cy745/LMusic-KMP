package com.lalilu.lplayer.notification

import com.lalilu.common.ext.io
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.toJsBlob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.w3c.dom.url.URL
import kotlin.coroutines.CoroutineContext


object BrowserMediaSessionHelper : CoroutineScope, KoinComponent {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val mediaSession: MediaSession? by lazy { mediaSessionOrNull() }
    private val platformMediaSource: PlatformMediaSource by inject()

    fun bindPlayback(playback: Playback) {
        val session = mediaSession ?: return

        session.setActionHandler("play") {
            launch { playback.play() }
        }
        session.setActionHandler("pause") {
            launch { playback.pause() }
        }
        session.setActionHandler("previoustrack") {
            launch { playback.skipToPrevious() }
        }
        session.setActionHandler("nexttrack") {
            launch { playback.skipToNext() }
        }

        playback.currentItem.onEach { item ->
            item?.let {
                createMetadata(
                    it.title,
                    it.subtitle,
                    it.subtitle
                )
            }?.let {
                setMetadata(it)
            }

            loadAlbumArtwork(item)
        }.launchIn(this)
    }

    private suspend fun loadAlbumArtwork(
        item: LAudio?
    ) = withContext(Dispatchers.io) {
        item ?: return@withContext

        val source = platformMediaSource.sources
            .firstOrNull { it.name == item.mediaSourceName }
            ?.dataSource
            ?: throw IllegalArgumentException("MediaSource not found")

        val pictureData = source.getPicture(item)
            ?: return@withContext

        val url = when (pictureData) {
            is MediaData.Bytes -> {
                val blob = pictureData.bytes.toJsBlob()
                URL.createObjectURL(blob)
            }

            is MediaData.Url -> pictureData.url
        }

        item.let {
            createMetadataWithArtwork(
                title = it.title,
                artist = it.subtitle,
                album = it.subtitle,
                url = url,
                size = "512x512",
                type = "image/png"
            )
        }?.let {
            setMetadata(it)
        }
    }
}