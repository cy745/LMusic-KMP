package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.lalilu.extensions.bindToLifecycle
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.rememberMediaCoverRequest
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.viewmodel.PlayerViewModel
import com.lalilu.navigation.Screen
import org.koin.compose.viewmodel.koinViewModel

@Destination("/player_pad")
class PlayerScreenForPad : Screen {

    @Composable
    override fun Content() {
        val vm = koinViewModel<PlayerViewModel>()
        vm.bindToLifecycle()

        val currentItem = vm.currentItem.collectAsState(null)
        val isPlaying = vm.isPlaying.collectAsState()
        val currentCover = rememberMediaCoverRequest(currentItem.value)
        val duration = LPlayer.instance.currentDuration.collectAsState(0L)
        val playbackPosition = rememberPlaybackPositionState(
            isPlaying = isPlaying.value,
            playbackKey = currentItem.value?.id,
        )

        PlayerScreenForPadContent(
            currentItem = currentItem,
            coverData = { currentCover },
            currentTime = playbackPosition.position,
            sampledPlaybackKey = { playbackPosition.sampledPlaybackKey },
            duration = duration,
            isPlaying = isPlaying,
            lyricContent = vm.lyricContent,
            queue = vm.currentQueue,
        )
    }
}
