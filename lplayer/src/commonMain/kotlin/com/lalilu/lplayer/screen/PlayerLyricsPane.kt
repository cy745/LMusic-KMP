package com.lalilu.lplayer.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricLayout
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.DragAnchor
import com.lalilu.lplayer.components.LyricGestureOverlay
import com.lalilu.lplayer.components.PlayerScaffoldScope
import com.lalilu.lplayer.components.SeekbarPositionState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * 播放页歌词区域。
 *
 * 组件内部完整拥有歌词列表位置、预热、手势生命周期和手动浏览模式。外部只提供
 * 播放时间与 Scaffold 的布局能力，并接收“正在手动浏览”的结果来联动控制条显隐。
 */
@Composable
internal fun PlayerLyricsPane(
    modifier: Modifier = Modifier,
    scaffold: PlayerScaffoldScope,
    timeline: SeekbarPositionState,
    currentTime: () -> Long,
    lyricEntry: State<List<LyricItem>>,
    screenConstraints: Constraints,
    onManualScrollingChanged: (Boolean) -> Unit,
) {
    val state = rememberPlayerLyricsPaneState()
    val nestedScrollConnection = rememberLyricNestedScrollConnection(
        scaffold = scaffold,
        lyricsState = state,
    )
    val currentAnchor = scaffold.currentAnchor
    val isManualScrolling = state.isManualScrolling(currentAnchor)
    val currentOnManualScrollingChanged = rememberUpdatedState(onManualScrollingChanged)
    val shouldComposeLyrics by remember(scaffold, state) {
        derivedStateOf {
            scaffold.middleToMaxProgress > 0f ||
                    state.preparationRequested ||
                    state.gestureInProgress
        }
    }
    val inputBlocked by remember(scaffold) {
        derivedStateOf { !scaffold.expandedContentInteractive }
    }
    val accelerateDecelerate: (Float) -> Float = remember {
        { value -> ((cos((value + 1) * PI) / 2.0f) + 0.5f).toFloat() }
    }
    val arcTranslation: (Float) -> Float = remember {
        { value -> -2f * (value - 0.5f).pow(2) + 0.5f }
    }

    SideEffect {
        state.onAnchorChanged(currentAnchor)
        currentOnManualScrollingChanged.value(isManualScrolling)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnManualScrollingChanged.value(false) }
    }

    Box(modifier = modifier) {
        ReusableLyricHost(
            active = shouldComposeLyrics,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        ) {
            LyricLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val middleToMax = scaffold.middleToMaxProgress
                        val lyricAlpha = (2f * accelerateDecelerate(middleToMax) - 1f)
                            .coerceAtLeast(0f)
                        val layoutCompensation = size.height - scaffold.position
                        val gestureEmphasis = arcTranslation(middleToMax) * 600f

                        translationY = gestureEmphasis + layoutCompensation
                        alpha = lyricAlpha
                    }
                    .then(
                        if (inputBlocked) Modifier.clearAndSetSemantics { }
                        else Modifier,
                    ),
                listState = state.listState,
                currentTime = {
                    timeline.positionFor(currentTime().toFloat()).toLong()
                },
                screenConstraints = screenConstraints,
                lyricEntry = lyricEntry,
                isUserClickEnable = { true },
                isUserScrollEnable = {
                    state.isManualScrolling(scaffold.currentAnchor)
                },
                onPositionReset = state::followPlayback,
                onItemClick = { PlayerAction.SeekTo(it.time).action() },
                onItemLongClick = { state.enterManualScrolling() },
            )
        }

        if (inputBlocked) {
            LyricGestureOverlay(
                modifier = Modifier.fillMaxSize(),
                onGestureStarted = scaffold::cancelDrag,
                onDrag = { delta ->
                    scaffold.dragBy(delta)
                    val middle = scaffold.anchorPosition(DragAnchor.Middle)
                    if (middle != null && scaffold.position > middle) {
                        state.requestPreparation()
                    }
                },
                onGestureStopped = { velocity ->
                    try {
                        scaffold.flingAsync(velocity)
                    } finally {
                        state.finishPreparation()
                    }
                },
            )
        }
    }
}

private enum class LyricsInteractionMode {
    FollowPlayback,
    ManualScrolling,
}

/** 歌词区域自己的交互状态机，不向播放页泄露可变实现状态。 */
@Stable
private class PlayerLyricsPaneState(
    val listState: LazyListState,
) {
    private var interactionMode by mutableStateOf(LyricsInteractionMode.FollowPlayback)

    var gestureInProgress by mutableStateOf(false)
        private set
    var preparationRequested by mutableStateOf(false)
        private set

    fun isManualScrolling(anchor: DragAnchor): Boolean =
        interactionMode == LyricsInteractionMode.ManualScrolling && anchor == DragAnchor.Max

    fun onAnchorChanged(anchor: DragAnchor) {
        if (anchor != DragAnchor.Max) followPlayback()
    }

    fun beginGesture() {
        gestureInProgress = true
    }

    fun finishGesture() {
        gestureInProgress = false
    }

    fun enterManualScrolling() {
        interactionMode = LyricsInteractionMode.ManualScrolling
    }

    fun followPlayback() {
        interactionMode = LyricsInteractionMode.FollowPlayback
    }

    fun requestPreparation() {
        preparationRequested = true
    }

    fun finishPreparation() {
        preparationRequested = false
    }
}

@Composable
private fun rememberPlayerLyricsPaneState(): PlayerLyricsPaneState {
    val listState = rememberLazyListState()
    return remember(listState) { PlayerLyricsPaneState(listState) }
}

@Composable
private fun rememberLyricNestedScrollConnection(
    scaffold: PlayerScaffoldScope,
    lyricsState: PlayerLyricsPaneState,
): NestedScrollConnection {
    val haptic = LocalHapticFeedback.current

    return remember(scaffold, lyricsState, haptic) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                scaffold.cancelDrag()

                if (source == NestedScrollSource.UserInput) {
                    lyricsState.beginGesture()
                }

                if (
                    !lyricsState.isManualScrolling(scaffold.currentAnchor) &&
                    available.y > 0f &&
                    source == NestedScrollSource.UserInput &&
                    scaffold.position.toInt() == scaffold.anchorPosition(DragAnchor.Max)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    lyricsState.enterManualScrolling()
                }

                return if (lyricsState.isManualScrolling(scaffold.currentAnchor)) {
                    super.onPreScroll(available, source)
                } else {
                    if (source == NestedScrollSource.UserInput) {
                        scaffold.dragBy(available.y)
                    }
                    available
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                return if (lyricsState.isManualScrolling(scaffold.currentAnchor)) {
                    super.onPostScroll(consumed, available, source)
                } else {
                    scaffold.dragBy(available.y)
                    available
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!lyricsState.isManualScrolling(scaffold.currentAnchor)) {
                    return try {
                        scaffold.fling(available.y)
                        available
                    } finally {
                        lyricsState.finishGesture()
                    }
                }

                return super.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return try {
                    scaffold.fling(0f)
                    super.onPostFling(consumed, available)
                } finally {
                    lyricsState.finishGesture()
                }
            }
        }
    }
}

/**
 * 为停用的歌词节点提供一个始终活跃的测量边界。
 *
 * [ReusableContentHost] 会保留已经停用的 LayoutNode 以供下次复用；如果直接把这些节点放进
 * BoxWithConstraints，在共享元素的 Lookahead 测量中父布局仍可能尝试测量它们。这里由外层
 * Layout 在停用期间跳过子节点测量，重新激活后才恢复正常的测量和放置。
 */
@Composable
private fun ReusableLyricHost(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            ReusableContentHost(active = active) {
                ReusableContent(key = "PlayerLyrics", content = content)
            }
        },
    ) { measurables, constraints ->
        if (!active) {
            layout(constraints.maxWidth, constraints.maxHeight) { }
        } else {
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { it.place(0, 0) }
            }
        }
    }
}
