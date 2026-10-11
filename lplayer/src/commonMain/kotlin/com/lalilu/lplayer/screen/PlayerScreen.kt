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
            isPlaying = isPlaying,
            playbackKey = currentItem.value?.id,
        )
        val backgroundColor = rememberPlayerBackgroundColor()
        // Keep callback identities across item/playback updates; values remain read at use sites.
        val coverProvider = remember(currentCover) { { currentCover.value } }
        val sampledKeyProvider = remember(playbackPosition) { { playbackPosition.sampledPlaybackKey } }
        val backgroundProvider = remember(backgroundColor) { { backgroundColor.value } }
        val updateSeedColor = remember(seedColor) { { color: Color -> seedColor.value = color } }

        PlayerScreenContent(
            currentItem = currentItem,
            currentCover = coverProvider,
            currentTime = playbackPosition.position,
            sampledPlaybackKey = sampledKeyProvider,
            duration = duration,
            isPlaying = isPlaying,
            lyricContent = vm.lyricContent,
            queue = vm.currentQueue,
            backgroundColor = backgroundProvider,
            onSeedColorChanged = updateSeedColor,
        )
    }
}

/**
 * 播放页背景色：跟随主题的 `primaryContainer` 做弹簧过渡。
 *
 * 此函数返回 State，编译器不会为它生成独立的 restart scope。
 * 全局 MaterialKolor 颜色动画已关闭，主题只在目标方案变化时更新。
 * 此处保留局部背景过渡，动画 State 在绘制期读取，避免逐帧使页面组合失效。
 */
@Composable
internal fun rememberPlayerBackgroundColor(): State<Color> = animateColorAsState(
    targetValue = MaterialTheme.colorScheme.primaryContainer,
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "PlayerBackgroundColor",
)
