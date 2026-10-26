package com.lalilu.lplayer.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.util.UnstableApi
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import coil3.size.Precision
import com.lalilu.lmedia.MediaCoverRequest
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.core.CoreSessionPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

/** Feed the same cover used by the app into MediaSession, including Android 13+ system media UI. */
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
fun CoroutineScope.bindSessionArtwork(
    context: Context,
    player: CoreSessionPlayer,
    sources: PlatformMediaSource,
    repository: AudioRepository,
): Job = launch(Dispatchers.Main.immediate) {
    player.core.state.map { it.selected }.distinctUntilChanged().flatMapLatest { key ->
        if (key == null) flowOf(null to 0L)
        else sources.findSource(key.source)?.contentState
            ?.map { key to it.forItem(key.id).generation }
            ?: flowOf(key to 0L)
    }.distinctUntilChanged().collectLatest { (key, generation) ->
        if (key == null) {
            player.setSessionArtwork(null, null)
            return@collectLatest
        }
        try {
            val data = withContext(Dispatchers.IO) {
                val audio = repository.getAudioByPlaybackId(MediaKey(key.source, key.id).stableKey).filterNotNull().first()
                val result = context.imageLoader.execute(ImageRequest.Builder(context)
                    .data(MediaCoverRequest(audio, generation))
                    .size(256, 256)
                    .precision(Precision.EXACT)
                    .allowHardware(false)
                    .build())
                result.image?.toBitmap()?.let { bitmap ->
                    ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        output.toByteArray()
                    }
                }
            }
            ensureActive()
            player.setSessionArtwork(key, data)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("SessionArtwork", "Could not load current artwork", error)
            // Artwork failure must not interrupt playback. A content generation update retries.
        }
    }
}
