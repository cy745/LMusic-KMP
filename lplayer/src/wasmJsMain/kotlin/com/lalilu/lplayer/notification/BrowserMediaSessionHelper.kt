package com.lalilu.lplayer.notification

import co.touchlab.kermit.Logger
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
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.w3c.dom.url.URL
import kotlin.coroutines.CoroutineContext


object BrowserMediaSessionHelper : CoroutineScope, KoinComponent {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val logger = Logger.withTag("BrowserMediaSessionHelper")
    private val mediaSession: MediaSession? by lazy { mediaSessionOrNull() }
    private val platformMediaSource: PlatformMediaSource by inject()

    fun debugLog(message: String) = logger.i(message)

    fun bindPlayback(playback: Playback) {
        val session = mediaSession ?: return

        session.setActionHandler("play") {
            playback.play()
        }
        session.setActionHandler("pause") {
            playback.pause()
        }
        session.setActionHandler("previoustrack") {
            playback.skipTpPrevious()
        }
        session.setActionHandler("nexttrack") {
            playback.skipToNext()
        }

        playback.currentItem().onEach { item ->
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