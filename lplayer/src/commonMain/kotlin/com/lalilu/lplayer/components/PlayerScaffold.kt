package com.lalilu.lplayer.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import com.lalilu.extensions.ClassicBackHandler
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 播放页三段式布局向各内容区域暴露的布局信息和手势能力。
 *
 * 锚点、贴边动画与拖动互斥都由 [PlayerScaffold] 持有。内容区域只读取布局进度，或把自己
 * 收到的手势继续交给布局处理，不再共同维护一份页面级交互状态。
 */
@Stable
internal interface PlayerScaffoldScope {
    val currentAnchor: DragAnchor
    val position: Float
    val middleToMaxProgress: Float
    val middleToMinProgress: Float
    val minToMiddleProgress: Float
    val expandedContentInteractive: Boolean

    fun anchorPosition(anchor: DragAnchor): Int?
    fun cancelDrag()
    fun dragBy(delta: Float): Float
    suspend fun fling(velocity: Float): Float
    fun flingAsync(velocity: Float)

    /**
     * 让播放列表回到顶部。
     *
     * 对外暴露的是「意图」而非滚动机制：列表的滚动状态由本 Scaffold 持有（它负责列表的
     * 测量放置），调用方（例如 toolbar 的双击手势）不需要知道它是 [LazyListState]，
     * 也不必自己判断列表是否为空。
     */
    fun scrollPlaylistToTop()
}

@Stable
private class DefaultPlayerScaffoldScope(
    private val draggable: CustomAnchoredDraggableState,
    private val expandedContentUnlocked: MutableState<Boolean>,
    private val playlistState: LazyListState,
    private val scope: CoroutineScope,
) : PlayerScaffoldScope {
    override val currentAnchor: DragAnchor
        get() = draggable.state.value
    override val position: Float
        get() = draggable.position.floatValue
    override val middleToMaxProgress: Float
        get() = draggable.progressBetween(DragAnchor.Middle, DragAnchor.Max)
    override val middleToMinProgress: Float
        get() = draggable.progressBetween(DragAnchor.Middle, DragAnchor.Min)
    override val minToMiddleProgress: Float
        get() = draggable.progressBetween(DragAnchor.Min, DragAnchor.Middle)
    override val expandedContentInteractive: Boolean
        get() = expandedContentUnlocked.value

    override fun anchorPosition(anchor: DragAnchor): Int? = draggable.getPositionByAnchor(anchor)
    override fun cancelDrag() = draggable.tryCancel()
    override fun dragBy(delta: Float): Float = draggable.dispatchRawDelta(delta)
    override suspend fun fling(velocity: Float): Float = draggable.fling(velocity)
    override fun flingAsync(velocity: Float) = draggable.flingAsync(velocity)

    override fun scrollPlaylistToTop() {
        // 空列表直接返回，避免把「列表是否为空」这种细节暴露给调用方
        if (playlistState.layoutInfo.totalItemsCount == 0) return
        scope.launch { playlistState.animateScrollToItem(0) }
    }
}

/**
 * 手机播放页的三段式骨架。
 *
 * 它只负责 Min / Middle / Max 三个锚点、各区域的测量放置、返回收起以及播放列表的
 * nested scroll。歌词如何准备、何时允许点击等逻辑由歌词区域自行管理。
 */
@Composable
internal fun PlayerScaffold(
    modifier: Modifier = Modifier,
    toolbarContent: @Composable PlayerScaffoldScope.() -> Unit = {},
    dynamicHeaderContent: @Composable PlayerScaffoldScope.() -> Unit = {},
    playlistContent: @Composable PlayerScaffoldScope.(Modifier, LazyListState) -> Unit = { _, _ -> },
    overlayContent: @Composable BoxScope.(PlayerScaffoldScope) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val expandedContentUnlocked = rememberSaveable { mutableStateOf(false) }
    val draggable = rememberCustomAnchoredDraggableState(
        onStateChange = { oldState, newState ->
            if (newState == DragAnchor.MiddleXMax && oldState != DragAnchor.MiddleXMax) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (newState == DragAnchor.Middle) {
                expandedContentUnlocked.value = false
            }
        },
        onSettleTargetSelected = { target ->
            if (target == DragAnchor.Max) {
                expandedContentUnlocked.value = true
            }
        },
    )
    val playlistState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scaffoldScope = remember(draggable, expandedContentUnlocked, playlistState) {
        DefaultPlayerScaffoldScope(
            draggable = draggable,
            expandedContentUnlocked = expandedContentUnlocked,
            playlistState = playlistState,
            scope = scope,
        )
    }

    ClassicBackHandler(
        enabled = draggable.state.value == DragAnchor.Max,
        onBack = {
            if (draggable.state.value == DragAnchor.Max) {
                draggable.animateToState(DragAnchor.Middle)
            }
        },
    )

    val playlistNestedScrollConnection = remember(draggable) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                draggable.tryCancel()

                if (available.y < 0f) {
                    return available.copy(y = draggable.dispatchRawDelta(available.y))
                }

                return super.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f) {
                    val consumedY = draggable.dispatchRawDelta(available.y)

                    if ((available.y - consumedY) > 0.005f && source == NestedScrollSource.SideEffect) {
                        throw CancellationException()
                    }
                    return available.copy(y = consumedY)
                }

                return super.onPostScroll(consumed, available, source)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                draggable.fling(available.y)

                return if (available.y > 0) {
                    // 向下时消耗剩余速度，避免继续传给外层 OverScroll。
                    available
                } else {
                    // 向上时保留剩余速度，继续传给外层 OverScroll。
                    super.onPostFling(consumed, available)
                }
            }
        }
    }

    Box(modifier = modifier) {
        Layout(
            content = {
                scaffoldScope.toolbarContent()
                scaffoldScope.dynamicHeaderContent()
                scaffoldScope.playlistContent(
                    Modifier.nestedScroll(playlistNestedScrollConnection),
                    playlistState,
                )
            },
        ) { measurables, constraints ->
            val toolbar = measurables[0].measure(constraints)
            val background = measurables[1].measure(constraints)
            val playlistConstraints = constraints.copy(
                maxHeight = constraints.maxHeight - toolbar.height,
            )
            val playlist = measurables[2].measure(playlistConstraints)

            draggable.updateAnchor(
                min = toolbar.height,
                // 中间锚点不能超过容器高度的一半。
                middle = constraints.maxWidth.coerceAtMost(constraints.maxHeight / 2),
                max = constraints.maxHeight,
            )

            layout(
                width = constraints.maxWidth,
                height = constraints.maxHeight,
            ) {
                val offset = draggable.position.floatValue.roundToInt()
                    .coerceIn(toolbar.height, constraints.maxHeight)

                background.place(0, offset - background.height)
                toolbar.place(0, offset - toolbar.height)
                playlist.place(0, offset)
            }
        }

        overlayContent(scaffoldScope)
    }
}
