package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.player.ByteArrayCallbackMedia
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import kotlin.math.abs

/** Called only inside the player's serialized command boundary. Leaves decoded media paused.
 * Native prepare() only accepts a descriptor; it does not establish that audio can be opened.
 */
internal suspend fun prepareVlcMedia(player: MediaPlayer, data: MediaData, position: Long = 0) {
    currentCoroutineContext().ensureActive()
    // Clear the previous native state before looking for PAUSED, rather than relying
    // on an unqualified paused callback that may have come from an earlier command.
    player.controls().stop()
    try {
        // Open first, then clamp the saved position to the actual native duration.
        // Passing an out-of-range :start-time could end the item before it is ready.
        val options = arrayOf(":start-paused")
        val accepted = when (data) {
            is MediaData.Url -> player.media().prepare(data.url, *options)
            is MediaData.Bytes -> player.media().prepare(ByteArrayCallbackMedia.obtain(data.bytes), *options)
        }
        check(accepted) { "VLC rejected media" }
        player.controls().play()
        withTimeout(30_000) {
            while (true) {
                when (player.status().state()) {
                    State.PAUSED -> {
                        // Full VLC also accepts non-audio inputs (e.g. subtitle
                        // text). A paused input alone is not playable audio.
                        check(player.audio().track() >= 0) { "VLC found no selected audio track" }
                        return@withTimeout
                    }
                    State.ERROR, State.ENDED -> error("VLC failed to prepare audio")
                    else -> delay(25)
                }
            }
        }
        val duration = player.status().length()
        val target = if (duration > 0) position.coerceIn(0, duration) else position.coerceAtLeast(0)
        player.controls().setTime(target)
        withTimeout(5_000) {
            while (abs(player.status().time() - target) > 500) delay(25)
        }
        currentCoroutineContext().ensureActive()
    } catch (failure: Exception) {
        withContext(NonCancellable) {
            try { player.controls().stop() }
            catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        }
        throw failure
    }
}
