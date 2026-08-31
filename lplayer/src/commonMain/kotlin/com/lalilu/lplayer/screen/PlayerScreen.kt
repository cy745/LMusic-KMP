package com.lalilu.lplayer.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.lalilu.LocalSeedColor
import com.lalilu.RemixIcon
import com.lalilu.extensions.bindToLifecycle
import com.lalilu.extensions.retrieve
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.rememberMediaCoverRequest
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.lplayer.generated.resources.Res
import com.lalilu.lplayer.lplayer.generated.resources.player_screen_title
import com.lalilu.lplayer.viewmodel.PlayerViewModel
import com.lalilu.navigation.Metadata
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.ScreenMetadataFactory
import org.koin.compose.viewmodel.koinViewModel

@Destination("/player")
class PlayerScreen : Screen, ScreenMetadataFactory, ScreenInfoFactory {

    override fun provideMetadata(): Map<String, Any> = Metadata.player()

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { Res.string.player_screen_title.retrieve() },
            icon = RemixIcon.Media.music2Line,
        )
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val vm = koinViewModel<PlayerViewModel>()
        vm.bindToLifecycle()

        val seedColor = LocalSeedColor.current
        val isPlaying = vm.isPlaying.collectAsState()
        val currentItem = vm.currentItem.collectAsState(null)
        val currentCover = rememberMediaCoverRequest(currentItem.value)
        val duration = LPlayer.instance.currentDuration.collectAsState(0L)
        val currentTime = rememberPlaybackPositionState(
            isPlaying = isPlaying.value,
            playbackKey = currentItem.value?.id,
        )
        val state = rememberPlayerScreenState {
            currentTime.longValue.toFloat()
        }
        val backgroundColor = animateColorAsState(
            targetValue = MaterialTheme.colorScheme.primaryContainer,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "PlayerBackgroundColor",
        )

        PlayerScreenContent(
            state = state,
            currentItem = currentItem,
            currentCover = { currentCover },
            currentTime = currentTime,
            duration = duration,
            isPlaying = isPlaying,
            lyricEntry = vm.lyricItems,
            queue = vm.currentQueue,
            backgroundColor = backgroundColor,
            onSeedColorChanged = { seedColor.value = it },
        )
    }
}
