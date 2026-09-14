package com.lalilu.lplayer.playback

import androidx.media3.common.Player
import com.lalilu.lplayer.extensions.toMediaItem

/** Must run on the player's thread: the final ownership check and native writes cannot interleave. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal suspend fun restorePausedQueueIfOwned(
    bridge: PlatformQueueBridge,
    player: Player,
    expected: QueueState,
    original: QueueState,
    position: Long,
    prepare: Boolean,
    beforeApply: () -> Unit = {},
): Boolean = bridge.editAndApplyIf(
    predicate = { state ->
        state.stillOwnsEdit(expected) &&
            (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId } == expected.list.map { it.playbackId } &&
            (expected.list.isEmpty() || player.currentMediaItemIndex == expected.index)
    },
    block = { replaceAll(original.list, original.index) },
    apply = { restored ->
        beforeApply()
        player.pause()
        player.setMediaItems(restored.list.map { it.toMediaItem() }, restored.index, position.coerceAtLeast(0))
        if (prepare && restored.list.isNotEmpty()) player.prepare()
    },
)
