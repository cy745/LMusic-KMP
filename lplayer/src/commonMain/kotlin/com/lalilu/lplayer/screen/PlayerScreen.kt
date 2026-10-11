package com.lalilu.lplayer.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.lalilu.LocalSeedColor
import com.lalilu.RemixIcon
import com.lalilu.extensions.bindToLifecycle
import com.lalilu.extensions.retrieve
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.rememberMediaCoverRequestState
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.debug.PlayerLoadTestHarness
import com.lalilu.lplayer.lplayer.generated.resources.Res
import com.lalilu.lplayer.lplayer.generated.resources.player_screen_title
import com.lalilu.lplayer.viewmodel.PlayerViewModel
import com.lalilu.navigation.Metadata
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.ScreenMetadataFactory
import com.skydoves.compose.stability.runtime.TraceRecomposition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        val rawDuration = LPlayer.instance.currentDuration.collectAsState(0L)
        val buffered = LPlayer.instance.currentBufferedPosition.collectAsState(0L)
        val engineBuffering = LPlayer.instance.isBuffering.collectAsState(false)
        val engineMediaId = LPlayer.instance.currentMediaId.collectAsState(null)
        val lab = PlayerLoadTestHarness.profile.collectAsState()
        val forcedBuffering = PlayerLoadTestHarness.forcedBuffering.collectAsState()
        val playbackId = currentItem.value?.playbackId
        // 引擎上报的媒体身份与当前曲一致时，时长/缓冲才属于这一首。
        val matchingMedia = engineMediaId.value == null || engineMediaId.value == playbackId
        var durationGate by remember(playbackId, lab.value) {
            mutableStateOf(!lab.value.enabled || (lab.value.durationDelayMs == 0L && !lab.value.unknownDuration))
        }
        var audioGate by remember(playbackId, lab.value) {
            mutableStateOf(!lab.value.enabled || lab.value.audioDelayMs == 0L)
        }
        LaunchedEffect(playbackId, lab.value) {
            durationGate = !lab.value.enabled || (lab.value.durationDelayMs == 0L && !lab.value.unknownDuration)
            audioGate = !lab.value.enabled || lab.value.audioDelayMs == 0L
            if (lab.value.enabled) {
                launch { delay(lab.value.durationDelayMs); durationGate = !lab.value.unknownDuration }
                launch { delay(lab.value.audioDelayMs); audioGate = true }
            }
        }
        val duration = rememberUpdatedState(
            if (matchingMedia && durationGate) rawDuration.value.coerceAtLeast(0L) else 0L
        )
        // 歌词页展示的文档仍属于上一首时，正文保持冻结：它的绝对定位已经失效。
        val lyricsPending = vm.lyricContent.value.key != currentItem.value?.id
        val playbackPosition = rememberPlaybackPositionState(
            isPlaying = isPlaying,
            playbackKey = currentItem.value?.id,
            // 注入的再缓冲只冻结展示位置，真实的播放位置不受影响。
            frozen = { forcedBuffering.value },
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
            bufferedValue = {
                if (!matchingMedia || !audioGate) 0f
                else if (lab.value.enabled && forcedBuffering.value) playbackPosition.position.longValue.toFloat()
                else if (lab.value.enabled && lab.value.name != "local") {
                    (playbackPosition.position.longValue + 15_000L)
                        .let { if (duration.value > 0L) it.coerceAtMost(duration.value) else it }
                        .toFloat()
                } else buffered.value.toFloat()
            },
            engineBuffering = engineBuffering,
            isBuffering = { engineBuffering.value || !audioGate || forcedBuffering.value },
            lyricsPending = lyricsPending,
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
