package com.lalilu.llyricview

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.common.kv.KVItem
import com.lalilu.extensions.ClassicBackHandler
import com.lalilu.extensions.fadeEdge
import com.lalilu.extensions.rememberLazyListAnimateScroller
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.findPlayingIndex
import com.lalilu.llyricview.impl.LyricContentNormal
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import kotlin.math.PI
import kotlin.math.cos

private data class LyricPageKey(
    val mediaKey: String?,
    val generation: Long,
    val isReady: Boolean,
)

private const val ReducedLyricFadeOutDurationMillis = 120
private const val ReducedLyricFadeInDurationMillis = 220
private val LyricTransitionMaxBlur = 25.dp
private val LyricFadeEdgeEasing = Easing { fraction ->
    (cos((fraction + 1f) * PI) / 2.0 + 0.5).toFloat()
}

private val LyricContent.pageKey: LyricPageKey
    get() = LyricPageKey(
        mediaKey = key,
        generation = generation,
        isReady = this is LyricContent.Ready,
    )

private class LyricPageHistory {
    var lastReadyMediaKey: String? = null
}

/**
 * 以完整歌词文档为单位切换内容。
 *
 * 新旧页面在淡入淡出的同时从模糊状态过渡，以整份歌词作为视觉切换单位。每份歌词都拥有
 * 独立的列表、位置缓存和滚动任务；旧页面退出时被冻结，因此它的跟随动画不会继续作用到新歌词上。
 * 用户启用轻量过渡后，只执行顺序淡出、淡入，不再创建整页模糊效果。
 */
@Composable
fun LyricLayout(
    modifier: Modifier = Modifier,
    currentTime: () -> Long = { 0L },
    lyricContent: State<LyricContent> = remember {
        mutableStateOf(LyricContent.Ready(key = null, items = emptyList()))
    },
    sampledPlaybackKey: () -> Any? = { lyricContent.value.key },
    screenConstraints: Constraints,
    isUserClickEnable: () -> Boolean = { false },
    isUserScrollEnable: () -> Boolean = { false },
    onPositionReset: () -> Unit = {},
    onItemClick: (LyricItem) -> Unit = {},
    onItemLongClick: (LyricItem) -> Unit = {},
) {
    val settings: KVItem<LyricSettings> = koinInject(named("LyricSettings"))
    val content = lyricContent.value
    val reducedTransitionEnabled = settings.value.reducedTransitionEnabled
    val transition = updateTransition(
        targetState = content,
        label = "LyricContentTransition",
    )
    val pageHistory = remember { LyricPageHistory() }
    val startsFromBeginning = content is LyricContent.Ready &&
            content.key != null &&
            pageHistory.lastReadyMediaKey != null &&
            pageHistory.lastReadyMediaKey != content.key

    SideEffect {
        if (content is LyricContent.Ready && content.key != null) {
            pageHistory.lastReadyMediaKey = content.key
        }
    }

    LaunchedEffect(content.key, content.generation) {
        if (isUserScrollEnable()) onPositionReset()
    }

    transition.AnimatedContent(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
        contentKey = LyricContent::pageKey,
        transitionSpec = {
            if (reducedTransitionEnabled) {
                // 轻量方案让旧页面先完整淡出，再显示新页面，避免同时绘制两份歌词。
                val enterDelay = if (initialState is LyricContent.Loading) {
                    0
                } else {
                    ReducedLyricFadeOutDurationMillis
                }
                fadeIn(
                    animationSpec = tween(
                        durationMillis = ReducedLyricFadeInDurationMillis,
                        delayMillis = enterDelay,
                        easing = LinearOutSlowInEasing,
                    )
                ) togetherWith fadeOut(
                    animationSpec = tween(
                        durationMillis = ReducedLyricFadeOutDurationMillis,
                        easing = FastOutLinearInEasing,
                    )
                )
            } else {
                (fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessLow))) using
                        SizeTransform(clip = false) { _, _ -> snap() }
            }
        },
    ) { pageContent ->
        val isTargetPage = pageContent.pageKey == transition.targetState.pageKey
        val positionSynchronized = pageContent.key == sampledPlaybackKey()
        val transitionEffectModifier = if (reducedTransitionEnabled) {
            Modifier
        } else {
            val blurRadius = this.transition.animateDp(
                transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
                label = "LyricContentBlur",
            ) { state ->
                when (state) {
                    EnterExitState.Visible -> 0.dp
                    EnterExitState.PreEnter -> LyricTransitionMaxBlur
                    EnterExitState.PostExit -> LyricTransitionMaxBlur
                }
            }.value
            Modifier.blur(
                radius = blurRadius,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(transitionEffectModifier),
        ) {
            when (pageContent) {
                is LyricContent.Loading -> Spacer(modifier = Modifier.fillMaxSize())
                is LyricContent.Ready -> LyricPage(
                    modifier = Modifier.fillMaxSize(),
                    content = pageContent,
                    active = isTargetPage,
                    startFromBeginning = startsFromBeginning,
                    positionSynchronized = positionSynchronized,
                    followEnabled = isTargetPage && positionSynchronized && !transition.isRunning,
                    interactive = isTargetPage && positionSynchronized && !transition.isRunning,
                    currentTime = currentTime,
                    sampledPlaybackKey = sampledPlaybackKey,
                    screenConstraints = screenConstraints,
                    isUserClickEnable = isUserClickEnable,
                    isUserScrollEnable = isUserScrollEnable,
                    onPositionReset = onPositionReset,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                )
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun LyricPage(
    content: LyricContent.Ready,
    active: Boolean,
    startFromBeginning: Boolean,
    positionSynchronized: Boolean,
    followEnabled: Boolean,
    interactive: Boolean,
    currentTime: () -> Long,
    sampledPlaybackKey: () -> Any?,
    screenConstraints: Constraints,
    isUserClickEnable: () -> Boolean,
    isUserScrollEnable: () -> Boolean,
    onPositionReset: () -> Unit,
    onItemClick: (LyricItem) -> Unit,
    onItemLongClick: (LyricItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val settings: KVItem<LyricSettings> = koinInject(named("LyricSettings"))
    val textMeasurer = rememberTextMeasurer()
    val currentActive by rememberUpdatedState(active)
    val latestCurrentTime by rememberUpdatedState(currentTime)
    val latestSampledPlaybackKey by rememberUpdatedState(sampledPlaybackKey)
    val startsFromBeginning = remember(content.key, content.generation) {
        startFromBeginning
    }
    var preparedForFollowing by remember(content.key, content.generation) {
        mutableStateOf(false)
    }
    var frozenTime by remember(content.key, content.generation) {
        mutableLongStateOf(if (startsFromBeginning) 0L else latestCurrentTime())
    }
    val displayTime = remember {
        {
            if (
                currentActive &&
                latestSampledPlaybackKey() == content.key &&
                preparedForFollowing
            ) {
                latestCurrentTime()
            } else {
                frozenTime
            }
        }
    }
    val initialItemIndex = remember(content.key, content.generation) {
        if (startsFromBeginning || !positionSynchronized) {
            0
        } else {
            content.items
                .findPlayingIndex(latestCurrentTime() + settings.value.timeOffset)
                .takeUnless { it == Int.MAX_VALUE }
                ?.coerceIn(content.items.indices)
                ?: 0
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialItemIndex,
    )
    val itemKeys = remember(content.key, content.generation) {
        content.items.map(LyricItem::key)
    }
    val scroller = rememberLazyListAnimateScroller(
        listState = listState,
        enableScrollAnimation = {
            followEnabled && preparedForFollowing && !isUserScrollEnable()
        },
        keys = { itemKeys },
    )
    val currentItemIndex = remember(content.key, content.generation) {
        derivedStateOf {
            val time = displayTime() + settings.value.timeOffset
            content.items.findPlayingIndex(time)
        }
    }
    val currentItem: State<LyricItem?> = remember(content.key, content.generation) {
        derivedStateOf {
            currentItemIndex.value
                .takeIf { it != Int.MAX_VALUE }
                ?.let(content.items::getOrNull)
        }
    }
    val canInteract = interactive && preparedForFollowing

    LaunchedEffect(active, positionSynchronized, preparedForFollowing) {
        if (!active || !positionSynchronized || !preparedForFollowing) {
            return@LaunchedEffect
        }
        snapshotFlow {
            if (latestSampledPlaybackKey() == content.key) latestCurrentTime() else null
        }
            .filterNotNull()
            .collect { frozenTime = it }
    }

    LaunchedEffect(followEnabled, startsFromBeginning) {
        if (!followEnabled) return@LaunchedEffect

        val targetIndex = if (startsFromBeginning) {
            0
        } else {
            content.items
                .findPlayingIndex(latestCurrentTime() + settings.value.timeOffset)
                .takeUnless { it == Int.MAX_VALUE }
                ?.coerceIn(content.items.indices)
                ?: 0
        }
        listState.scrollToItem(targetIndex)
        withFrameNanos { }
        preparedForFollowing = true
    }

    LaunchedEffect(followEnabled, preparedForFollowing) {
        if (!followEnabled || !preparedForFollowing) return@LaunchedEffect
        snapshotFlow { currentItem.value }
            .collectLatest { item ->
                item ?: return@collectLatest
                scroller.animateTo(
                    key = item.key,
                    animationSpec = spring(
                        dampingRatio = settings.value.scrollSpringDampingRatio,
                        stiffness = settings.value.scrollSpringStiffness,
                        visibilityThreshold = 0.001f,
                    )
                )
            }
    }

    LaunchedEffect(active, preparedForFollowing) {
        if (!active || !preparedForFollowing) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress to isUserScrollEnable() }
            .debounce(5000)
            .collectLatest { (isDragging, isScrolling) ->
                if (!isActive || isDragging || !isScrolling) return@collectLatest

                currentItem.value?.key?.let(scroller::animateTo)
                onPositionReset()
            }
    }

    ClassicBackHandler(
        enabled = active && preparedForFollowing && isUserScrollEnable(),
        onBack = {
            currentItem.value?.key?.let(scroller::animateTo)
            onPositionReset()
        },
    )

    val context = remember(
        content.key,
        content.generation,
        screenConstraints,
        textMeasurer,
    ) {
        LyricContext(
            currentTime = { displayTime() + settings.value.timeOffset },
            currentIndex = { currentItemIndex.value },
            isUserScrolling = isUserScrollEnable,
            screenConstraints = screenConstraints,
            textMeasurer = textMeasurer,
        )
    }

    Box(modifier = modifier) {
        val heightSplit = remember(screenConstraints) {
            density.run { screenConstraints.maxHeight.toDp() / 3f }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .fadeEdge(
                    top = heightSplit,
                    bottom = heightSplit,
                    easing = LyricFadeEdgeEasing,
                ),
            userScrollEnabled = canInteract,
            contentPadding = PaddingValues(
                top = heightSplit,
                bottom = heightSplit * 2f,
            ),
        ) {
            if (content.items.isEmpty()) {
                item(key = "EMPTY_TIPS") {
                    val item = remember {
                        LyricItem.NormalLyric(
                            key = "0",
                            content = "暂无歌词",
                            time = 0L,
                        )
                    }

                    LyricContentNormal(
                        lyric = item,
                        index = context.currentIndex(),
                        modifier = Modifier,
                        settings = settings.value,
                        context = context,
                        onLongClick = {
                            if (canInteract && isUserClickEnable()) onItemLongClick(item)
                        },
                        onClick = {},
                    )
                }
            } else {
                itemsIndexed(
                    items = content.items,
                    key = { _, item -> item.key },
                    // 不同歌词类型的组合结构不同，只在同类型之间复用 slot。
                    contentType = { _, item -> item::class },
                ) { index, item ->
                    LyricItemLayout.get(item)?.content(
                        item = item,
                        index = index,
                        modifier = Modifier,
                        settings = settings.value,
                        context = context,
                        onLongClick = {
                            if (canInteract && isUserClickEnable()) onItemLongClick(item)
                        },
                        onClick = {
                            if (canInteract && isUserClickEnable()) onItemClick(item)
                        },
                    )
                }
            }
        }

        val contentColor = remember { Color.White }
        val colors = ButtonDefaults.textButtonColors(
            containerColor = contentColor.copy(alpha = 0.15f),
            contentColor = contentColor,
        )

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .fillMaxWidth(),
            enter = fadeIn() + slideIn { IntOffset(0, 100) },
            exit = fadeOut() + slideOut { IntOffset(0, 100) },
            visible = active && preparedForFollowing && isUserScrollEnable(),
        ) {
            TextButton(
                modifier = Modifier.wrapContentWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = colors,
                onClick = {
                    currentItem.value?.key?.let(scroller::animateTo)
                    onPositionReset()
                },
            ) {
                Text(
                    text = "退出歌词滚动模式",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
