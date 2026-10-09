package com.lalilu.lplayer.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lalilu.LocalSeedColor
import com.lalilu.RemixIcon
import com.lalilu.extensions.bindToLifecycle
import com.lalilu.extensions.retrieve
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.rememberMediaCoverRequestState
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.lplayer.generated.resources.Res
import com.lalilu.lplayer.lplayer.generated.resources.player_screen_title
import com.lalilu.lplayer.viewmodel.PlayerViewModel
import com.lalilu.navigation.Metadata
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.ScreenMetadataFactory
import com.skydoves.compose.stability.runtime.TraceRecomposition
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

    @TraceRecomposition(tag = "PlayerScreen", traceStates = true)
    @Composable
    override fun Content() {
        val vm = koinViewModel<PlayerViewModel>()
        vm.bindToLifecycle()

        val seedColor = LocalSeedColor.current
        val isPlaying = vm.isPlaying.collectAsState()
        val currentItem = vm.currentItem.collectAsState(null)
        val currentCover = rememberMediaCoverRequestState(currentItem.value)
        val duration = LPlayer.instance.currentDuration.collectAsState(0L)
        val playbackPosition = rememberPlaybackPositionState(
            isPlaying = isPlaying.value,
            playbackKey = currentItem.value?.id,
        )
        val backgroundColor = rememberPlayerBackgroundColor()

        PlayerScreenContent(
            currentItem = currentItem,
            currentCover = { currentCover.value },
            currentTime = playbackPosition.position,
            sampledPlaybackKey = { playbackPosition.sampledPlaybackKey },
            duration = duration,
            isPlaying = isPlaying,
            lyricContent = vm.lyricContent,
            queue = vm.currentQueue,
            backgroundColor = { backgroundColor.value },
            onSeedColorChanged = { seedColor.value = it },
        )
    }
}

/**
 * 播放页背景色：跟随主题的 `primaryContainer` 做弹簧过渡。
 *
 * 单独抽成一个 composable 是必须的：MaterialKolor 的主题动画（`animate = true`）会逐帧重建
 * ColorScheme，若在 [PlayerScreen] 的组合作用域里读 `MaterialTheme.colorScheme`，
 * 每帧都会重组整个播放页——连同 `PlayerDynamicHeader`、`BlurBackground` 与封面内容。
 * 收进这里之后，逐帧重组只发生在这一个小作用域内。
 */
@Composable
private fun rememberPlayerBackgroundColor(): State<Color> = animateColorAsState(
    targetValue = MaterialTheme.colorScheme.primaryContainer,
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "PlayerBackgroundColor",
)
