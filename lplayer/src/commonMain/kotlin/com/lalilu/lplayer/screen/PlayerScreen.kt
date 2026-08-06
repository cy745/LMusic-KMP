package com.lalilu.lplayer.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.LifecycleResumeEffect
import co.touchlab.kermit.Logger
import com.lalilu.LocalSeedColor
import com.lalilu.RemixIcon
import com.lalilu.common.ext.io
import com.lalilu.common.kv.KVItem
import com.lalilu.extensions.DialogItem
import com.lalilu.extensions.DialogWrapper
import com.lalilu.extensions.bindToLifecycle
import com.lalilu.extensions.retrieve
import com.lalilu.krouter.annotation.Destination
import com.lalilu.llyricview.LyricLayout
import com.lalilu.llyricview.LyricSettings
import com.lalilu.llyricview.provideLyricSettings
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.*
import com.lalilu.lplayer.extensions.PlayMode
import com.lalilu.lplayer.lplayer.generated.resources.Res
import com.lalilu.lplayer.lplayer.generated.resources.player_screen_title
import com.lalilu.lplayer.viewmodel.PlayerViewModel
import com.lalilu.navigation.*
import com.lalilu.lsettings.SettingsScreenContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

@Destination("/player")
class PlayerScreen : Screen, ScreenMetadataFactory, ScreenInfoFactory {

    override fun provideMetadata(): Map<String, Any> = Metadata.player()


    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { Res.string.player_screen_title.retrieve() },
            icon = RemixIcon.Media.music2Line
        )
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val seedColor = LocalSeedColor.current
        val vm = koinViewModel<PlayerViewModel>()
        vm.bindToLifecycle()

        val isPlaying = vm.isPlaying.collectAsState()
        val currentItem = vm.currentItem.collectAsState(null)
        val currentTime = vm.currentTime
        val isLyricScrollEnable = remember { mutableStateOf(false) }

        val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        val scope = rememberCoroutineScope()

        val draggable = rememberCustomAnchoredDraggableState { oldState, newState ->
            if (newState == DragAnchor.MiddleXMax && oldState != DragAnchor.MiddleXMax) {
                scope.launch(Dispatchers.io) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            if (newState != DragAnchor.Max) {
                isLyricScrollEnable.value = false
            }
        }
        val listState = rememberLazyListState()
        val duration = LPlayer.instance.currentDuration.collectAsState(0L)
        val animation = remember { Animatable(currentTime.value.toFloat()) }
        val navigationBar = WindowInsets.navigationBars

        val bgAnimateColor = animateColorAsState(
            targetValue = MaterialTheme.colorScheme.primaryContainer,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = ""
        )

        val middleToMaxProgress = remember {
            derivedStateOf {
                draggable.progressBetween(
                    from = DragAnchor.Middle,
                    to = DragAnchor.Max,
                    offset = draggable.position.floatValue
                )
            }
        }
        val middleToMinProgress = remember {
            derivedStateOf {
                draggable.progressBetween(
                    from = DragAnchor.Middle,
                    to = DragAnchor.Min,
                    offset = draggable.position.floatValue
                )
            }
        }
        val minToMiddleProgress = remember {
            derivedStateOf {
                draggable.progressBetween(
                    from = DragAnchor.Min,
                    to = DragAnchor.Middle,
                    offset = draggable.position.floatValue
                )
            }
        }

        LifecycleResumeEffect(isPlaying.value) {
            val job = scope.launch {
                while (isActive && isPlaying.value) {
                    withFrameMillis { currentTime.value = LPlayer.instance.currentPosition() }
                }
            }
            onPauseOrDispose {
                job.cancel()
            }
        }

        NestedScrollBaseLayout(
            draggable = draggable,
            isLyricScrollEnable = isLyricScrollEnable,
            toolbarContent = {
                Column(
                    modifier = Modifier
//                        .hideControl(
//                            enable = { hideComponent.value },
//                            intercept = { true }
//                        )
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 10.dp)
                        .graphicsLayer {
                            translationY = lerp(
                                start = 0f,
                                stop = -navigationBar
                                    .getBottom(density)
                                    .toFloat() + 10.dp.toPx(),
                                fraction = middleToMaxProgress.value
                            )

                            alpha =
                                (1.25f * (middleToMaxProgress.value + middleToMinProgress.value) - 0.25f)
                                    .coerceAtLeast(0f)
                        }
                ) {
                    PlayingToolbar(
                        modifier = Modifier.fillMaxWidth(),
                        title = { currentItem.value?.title ?: "LMusic" },
                        subtitle = { currentItem.value?.subtitle ?: "....." },
                        contentColor = { contentColor },
                        isPlaying = { isPlaying.value },
                        // 与旧项目一致：仅在播放面板完全展开时显示工具栏操作（带进入/退出动画）
                        isUserTouchEnable = {
                            draggable.state.value == DragAnchor.Min ||
                                draggable.state.value == DragAnchor.Max
                        },
                        isExtraVisible = { draggable.state.value == DragAnchor.Max },
                        extraContent = {
                            val lyricSettings = koinInject<KVItem<LyricSettings>>(
                                named("LyricSettings")
                            )
                            val translationAlpha = animateFloatAsState(
                                targetValue = if (lyricSettings.value.translationVisible) 1f else 0.5f,
                                label = ""
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        DialogWrapper.push(
                                            DialogItem.Dynamic(
                                                backgroundColor = Color.Transparent,
                                                content = {
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 560.dp)
                                                            .padding(horizontal = 16.dp)
                                                            .padding(bottom = 8.dp),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            MaterialTheme.colorScheme
                                                                .onBackground
                                                                .copy(alpha = 0.1f)
                                                        ),
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = MaterialTheme.colorScheme.background,
                                                    ) {
                                                        SettingsScreenContent(
                                                            groups = listOf(
                                                                provideLyricSettings(
                                                                    fontManager = koinInject(),
                                                                    includeFontEntry = false
                                                                )
                                                            ),
                                                            showNavigatorHeader = false,
                                                            // 弹层内不应用页面级 statusBar/smartBar inset：
                                                            // 默认 contentPadding 会按屏幕状态栏计算，导致弹窗顶部出现一截空白
                                                            contentPadding = PaddingValues(
                                                                top = 0.dp,
                                                                bottom = 8.dp
                                                            )
                                                        )
                                                    }
                                                }
                                            )
                                        )
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = vectorResource(RemixIcon.Editor.text),
                                        contentDescription = "歌词样式",
                                        tint = contentColor
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        lyricSettings.value = lyricSettings.value.copy(
                                            translationVisible = !lyricSettings.value.translationVisible
                                        )
                                        lyricSettings.save()
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = vectorResource(RemixIcon.Editor.translate2),
                                        contentDescription = "翻译",
                                        modifier = Modifier.graphicsLayer {
                                            alpha = translationAlpha.value
                                        },
                                        tint = contentColor
                                    )
                                }
                            }
                        }
                    )
                }
            },
            dynamicHeaderContent = { modifier ->
                BoxWithConstraints(
                    modifier = modifier.fillMaxSize()
                        .clipToBounds()
                        .drawBehind { drawRect(color = bgAnimateColor.value) }
                ) {
                    val adInterpolator: (Float) -> Float = remember {
                        { x -> ((cos((x + 1) * PI) / 2.0f) + 0.5f).toFloat() }
                    }
                    val dInterpolator: (Float) -> Float = remember {
                        { x -> (1.0f - (1.0f - x) * (1.0f - x)) }
                    }
                    val transition: (Float) -> Float = remember {
                        { x -> -2f * (x - 0.5f).pow(2) + 0.5f }
                    }

                    BlurBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .graphicsLayer {
                                val maxHeight = constraints.maxHeight
                                val maxWidth = constraints.maxWidth

                                // min至middle阶段中的位移
                                val minToMiddleInterpolated =
                                    dInterpolator.invoke(minToMiddleProgress.value)
                                val minToMiddleOffset =
                                    lerp(-size.width / 2f, 0f, minToMiddleInterpolated)

                                // middle至max阶段中的位移
                                val middleToMaxInterpolated =
                                    dInterpolator.invoke(middleToMaxProgress.value)
                                val middleToMaxOffset =
                                    lerp(0f, (maxHeight - maxWidth) / 2f, middleToMaxInterpolated)

                                // 用于补偿修正因layout时根据draggable的值进行布局的位移
                                val fixOffset = maxHeight - draggable.position.floatValue

                                // 添加凸显滑动时的动画的位移
                                val progressTransited = transition(middleToMaxProgress.value)
                                val additionalOffset = progressTransited * 200f

                                // 计算父级容器的长宽比，计算需要覆盖父级容器的的缩放比例的值scale
                                val aspectRatio = maxHeight.toFloat() / maxWidth.toFloat()
                                val scale = lerp(1f, aspectRatio, middleToMaxProgress.value)

                                translationY =
                                    minToMiddleOffset + middleToMaxOffset + fixOffset + additionalOffset
                                alpha = minToMiddleProgress.value
                                scaleY = scale
                                scaleX = scale
                            },
                        blurProgress = { middleToMaxProgress.value },
                        onColorPairFetched = { bgColor, cColor -> seedColor.value = bgColor },
                        imageData = { currentItem.value ?: "" }
                    )

                    LyricLayout(
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer {
                                val interpolation = adInterpolator.invoke(middleToMaxProgress.value)
                                val progressIncrease = (2f * interpolation - 1F).coerceAtLeast(0F)
                                val fixOffset = size.height - draggable.position.floatValue

                                val progressTransited = transition(middleToMaxProgress.value)
                                val additionalOffset = progressTransited * 200f * 3f

                                translationY = additionalOffset + fixOffset
                                alpha = progressIncrease
                            },
                        currentTime = { animation.value.toLong() },
                        screenConstraints = constraints,
                        lyricEntry = vm.lyricItems,
                        isUserClickEnable = { true },
                        isUserScrollEnable = { false },
                        onItemClick = { PlayerAction.SeekTo(it.time).action() },
                        onItemLongClick = {}
                    )
                }
            },
            playlistContent = { modifier ->
                PlaylistLayout(
                    modifier = modifier,
                    listState = listState,
                    items = vm.currentQueue,
                )
            },
            overlayContent = {
                val animateProgress = animateFloatAsState(
                    targetValue = if (!isLyricScrollEnable.value) 100f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = ""
                )

//                Spacer(
//                    modifier = Modifier.fillMaxSize()
//                        .drawBehind {
//                            val state = transitionState.value ?: return@drawBehind
//                            val back = state.targetState.key != homeScreen?.key
//                            val progress = if (back) 1f - state.fraction else state.fraction
//                            if (state.targetState != state.currentState) {
//                                drawRect(color = Color.Black.copy(0.8f * progress))
//                            }
//                        }
//                )

                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            alpha = animateProgress.value / 100f
                            translationY = (1f - animateProgress.value / 100f) * 500f
                        }
                ) {
                    val bottomSheetState = LocalModalBottomSheetState.current
                    SeekbarLayout(
                        modifier = Modifier
                            .padding(horizontal = 40.dp)
                            .padding(bottom = 100.dp),
                        animateColor = { bgAnimateColor.value },
                        animation = animation,
                        maxValue = { duration.value.toFloat() },
                        dataValue = { currentTime.value.toFloat() },
                        onDispatchDragOffset = { deltaY ->
                            scope.launch { bottomSheetState.anchoredDraggableState.dispatchRawDelta(deltaY) }
                        },
                        onDragStop = { result ->
                            scope.launch {
                                if (result == 0) {
                                    bottomSheetState.anchoredDraggableState.settle(0f)
                                } else {
                                    bottomSheetState.hide()
                                }
                            }
                        },
                        onSeekTo = { position ->
                            Logger.i("seekTo: $position")
                            PlayerAction.SeekTo(position.toLong()).action()
                        },
                        onSwitchTo = { index ->
                            val playMode = PlayMode.indexOf(index)

                            PlayerAction.SetPlayMode(playMode)
                                .action()
//                            DynamicTipsItem.Static(
//                                title = when (playMode) {
//                                    PlayMode.ListRecycle -> "列表循环"
//                                    PlayMode.RepeatOne -> "单曲循环"
//                                    PlayMode.Shuffle -> "随机播放"
//                                },
//                                subTitle = "切换播放模式",
//                            ).show()
                        },
                        onClick = { clickPart ->
                            scope.launch(Dispatchers.io) {
                                when (clickPart) {
                                    ClickPart.Start -> LPlayer.instance.skipToPrevious()
                                    ClickPart.Middle -> LPlayer.instance.togglePlayPause()
                                    ClickPart.End -> LPlayer.instance.skipToNext()
                                }
                            }
                        }
                    )
                }
            }
        )
    }
}
