package com.lalilu.lplayer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.lalilu.RemixIcon
import com.lalilu.lplayer.extensions.AccumulatedValue
import com.skydoves.compose.stability.runtime.TraceRecomposition
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.absoluteValue

private sealed interface SeekbarState {
    data object Idle : SeekbarState
    data object ProgressBar : SeekbarState
    data object Switcher : SeekbarState
    data object Cancel : SeekbarState
    data object Dispatcher : SeekbarState
}

sealed interface ClickPart {
    data object Start : ClickPart
    data object Middle : ClickPart
    data object End : ClickPart
}

private fun SeekbarState.isCanceled(): Boolean {
    return when (this) {
        is SeekbarState.Cancel, is SeekbarState.Dispatcher -> true
        else -> false
    }
}

/**
 * 手势期间需要读写、但不参与组合的状态。
 *
 * 用普通可变字段而不是 Compose State：这些值只在指针回调里读写，
 * 又必须绕过 `remember` 对首个组合的捕获（例如组件宽度变化后的拖动换算）。
 */
private class SeekbarGestureState {
    var width: Float = 0f
    var positionMs: Long = 0L

    /** 按下瞬间的时长域：手指下的 px→ms 换算在整个拖动期间必须保持不变。 */
    var durationMs: Long = 0L
}

@TraceRecomposition(tag = "SeekbarLayout", traceStates = true)
@Preview
@Composable
fun SeekbarLayout(
    modifier: Modifier = Modifier,
    positionMs: () -> Long = { 0L },
    durationMs: () -> Long = { 0L },
    progress: () -> Float = { 0f },
    bufferedProgress: () -> Float = { 0f },
    scrollThreadHold: Float = 200f,
    animateColor: () -> Color = { Color.DarkGray },
    onSeekStart: (Long) -> Unit = {},
    onSeekDrag: (Long) -> Unit = {},
    onSeekCancel: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onDragStart: suspend (Offset) -> Unit = {},
    onDragStop: suspend (Int) -> Unit = {},
    onDispatchDragOffset: (Float) -> Unit = {},
    onSwitchTo: (Int) -> Unit = {},
    onClick: (ClickPart) -> Unit = {}
) {
    val textStyle = remember {
        TextStyle.Default.copy(
            fontSize = 16.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            baselineShift = BaselineShift.None,
            textAlign = TextAlign.End,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
    }

    BoxWithConstraints(
        modifier = modifier
    ) {
        val boxSize = constraints
        val haptic = LocalHapticFeedback.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val seekbarPaddingBottom = remember { 100.dp }
        val seekbarHeight = remember { 56.dp }

        val gesture = remember { SeekbarGestureState() }
        // 每次重组都刷新宽度，手势回调里读到的是当前尺寸而不是首次组合时的尺寸。
        gesture.width = boxSize.maxWidth.toFloat()

        val switchMode = remember { mutableStateOf(false) }
        val switchModeX = remember { mutableFloatStateOf(0f) }
        val seekbarOffsetY = remember { mutableFloatStateOf(0f) }
        val seekbarState = remember { mutableStateOf<SeekbarState>(SeekbarState.Idle) }

        var isMoved by remember { mutableStateOf(false) }
        var isTouching by remember { mutableStateOf(false) }
        var isSeekDragActive by remember { mutableStateOf(false) }
        val isSwitching by remember { derivedStateOf { seekbarState.value is SeekbarState.Switcher } }
        val isCanceled by remember { derivedStateOf { seekbarState.value.isCanceled() } }

        val currentTimeText = durationToText(duration = { positionMs() })
        val animationJob = remember { mutableStateOf<Job?>(null) }

        /** 按下即把交互时间交给外部时间轴：手指按住期间时间不再跟随播放。 */
        fun beginSeekDrag() {
            if (isSeekDragActive || durationMs() <= 0L) return
            isSeekDragActive = true
            gesture.durationMs = durationMs()
            onSeekStart(gesture.positionMs)
        }

        /** 每次拖动只结算一次：提交到 [commit]，或交还给时间轴自己平滑回正。 */
        fun endSeekDrag(commit: Long?) {
            if (!isSeekDragActive) return
            isSeekDragActive = false
            if (commit != null) onSeek(commit) else onSeekCancel()
        }

        /**
         * 进度条状态迁移的唯一入口。
         *
         * 只有"拖到取消 / 播放模式切换区"才算放弃这次拖动；回到 Idle 不做结算，
         * 因为提交动作统一由手势结束时决定，避免依赖松手事件与拖动结束回调的先后顺序。
         */
        fun transitionTo(next: SeekbarState) {
            val old = seekbarState.value
            if (old == next) return

            seekbarState.value = next
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            when {
                next == SeekbarState.ProgressBar -> beginSeekDrag()
                // 取消、收回面板、切换播放模式：都算放弃这次拖动
                next != SeekbarState.Idle -> endSeekDrag(null)
            }
        }

        val offsetY = remember {
            derivedStateOf {
                val offsetY = seekbarOffsetY.floatValue
                    .coerceAtMost(0f)
                    .absoluteValue
                    .takeIf { it < (scrollThreadHold / 2f) }
                    ?: 0f
                (offsetY / (scrollThreadHold / 2f)).coerceIn(0f, 1f)
            }
        }
        val offsetYProgress = animateFloatAsState(
            targetValue = offsetY.value,
            animationSpec = spring(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioMediumBouncy
            ),
        )

        val draggableState = rememberDraggable2DState { offset ->
            val oldState = seekbarState.value

            val deltaY = offset.y
            val deltaX = offset.x

            // 直接记录Y轴上的滚动距离
            seekbarOffsetY.floatValue += deltaY

            // 根据当前状态控制进度变量
            when {
                isSwitching -> {
                    switchModeX.floatValue += deltaX
                }

                oldState == SeekbarState.ProgressBar -> {
                    val duration = gesture.durationMs
                    if (duration > 0L && gesture.width > 0f) {
                        val delta = deltaX / gesture.width * duration.toFloat()
                        gesture.positionMs = (gesture.positionMs + delta)
                            .toLong()
                            .coerceIn(0L, duration)
                        onSeekDrag(gesture.positionMs)
                    }
                }
            }

            // 根据Y轴滚动距离决定新的状态；时长未知时不允许进入进度条拖动
            val newState = when {
                seekbarOffsetY.floatValue < -scrollThreadHold -> SeekbarState.Dispatcher
                seekbarOffsetY.floatValue < -(scrollThreadHold / 2f) -> SeekbarState.Cancel
                switchMode.value -> SeekbarState.Switcher
                durationMs() > 0L -> SeekbarState.ProgressBar
                else -> SeekbarState.Idle
            }
            transitionTo(newState)

            when {
                oldState == newState -> {}

                oldState == SeekbarState.Dispatcher -> scope.launch { onDragStop(-1) }

                oldState == SeekbarState.Cancel && newState == SeekbarState.Dispatcher -> {
                    animationJob.value?.cancel()
                    animationJob.value = scope.launch {
                        val animationState = AnimationState(
                            initialValue = 0f,
                            initialVelocity = 100f,
                        )

                        var lastValue = 0f
                        val targetOffset = scrollThreadHold +
                                density.run { (seekbarPaddingBottom + seekbarHeight).toPx() }

                        animationState.animateTo(targetOffset) {
                            val dt = value - lastValue
                            lastValue = value
                            onDispatchDragOffset(-dt)
                        }
                    }
                }

                else -> {}
            }

            // 若当前状态为Dispatcher，则将滚动的位移量向外分发
            if (newState is SeekbarState.Dispatcher) {
                onDispatchDragOffset(deltaY)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(seekbarHeight)
                .pointerInput(Unit) {
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        // 记录按下瞬间的时间与时长域：按住期间由手指接管
                                        gesture.positionMs = positionMs()
                                        gesture.durationMs = durationMs()
                                        isTouching = true
                                        isMoved = false
                                        beginSeekDrag()
                                    }

                                    PointerEventType.Release -> {
                                        isTouching = false
                                        // 单纯点击不会产生拖动，立即把时间交还给时间轴；
                                        // 真正的拖动统一由 onDragEnd 结算。
                                        if (!isMoved) endSeekDrag(null)
                                    }
                                }
                            }
                        }
                    } finally {
                        // 指针被取消或节点离开组合时兜底，避免时间轴一直停在拖动状态。
                        isTouching = false
                        endSeekDrag(null)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        val width = gesture.width
                        val clickPart = when (position.x) {
                            in 0f..(width / 3f) -> ClickPart.Start
                            in (width * 2 / 3f)..width -> ClickPart.End
                            else -> ClickPart.Middle
                        }
                        onClick(clickPart)
                    }
                }
                .combineDetectDrag(
                    onLongClickStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        switchModeX.floatValue = it.x
                        switchMode.value = true
                        transitionTo(SeekbarState.Switcher)
                    },
                    onDragStart = {
                        isMoved = true
                        seekbarOffsetY.floatValue = it.y
                        transitionTo(
                            when {
                                switchMode.value -> SeekbarState.Switcher
                                durationMs() > 0L -> SeekbarState.ProgressBar
                                else -> SeekbarState.Idle
                            }
                        )
                        scope.launch { onDragStart(it) }
                    },
                    onDragEnd = {
                        // 结算前先看清这次拖动是被放弃的还是正常松手
                        val abandoned = isCanceled || isSwitching
                        val canCommit = isMoved && !abandoned && durationMs() > 0L

                        if (isSwitching) {
                            val actualWidth = gesture.width - density.run { 4.dp.roundToPx() }
                            val singleWidth = actualWidth / 3f

                            when (switchModeX.floatValue) {
                                in 0f..singleWidth -> onSwitchTo(0)
                                in singleWidth..(singleWidth * 2) -> onSwitchTo(1)
                                else -> onSwitchTo(2)
                            }
                        }

                        switchMode.value = false
                        transitionTo(SeekbarState.Idle)
                        endSeekDrag(gesture.positionMs.takeIf { canCommit })

                        seekbarOffsetY.floatValue = 0f
                        animationJob.value?.cancel()
                        animationJob.value = null
                        scope.launch { onDragStop(0) }
                    },
                    onDrag = { _, dragAmount ->
                        draggableState.dispatchRawDelta(dragAmount)
                    }
                )
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen

                    translationY = -offsetYProgress.value * (scrollThreadHold / 2f)
                    scaleX = 1f - (offsetYProgress.value * 0.1f)
                    scaleY = scaleX
                }
                .clip(RoundedCornerShape(16.dp))
        ) {
            SeekbarBackground(visible = { isTouching && !isCanceled })
            SeekbarContentMask(clip = { isTouching && !isCanceled })
            // 层级即声明顺序：背景 < 遮罩 < 时长 < 滑块（缓冲条 + 播放进度）< 当前时间。
            // 时长元素必须留在滑块之下，不要在这里加 zIndex。
            SeekbarMaxDuration(
                modifier = Modifier
                    .align(Alignment.CenterEnd),
                visible = { !isSwitching },
                textStyle = textStyle,
                durationMs = durationMs,
            )
            SeekbarThumb(
                clip = { isTouching && !isCanceled },
                thumbColor = animateColor,
                progress = progress,
                bufferedProgress = bufferedProgress,
                switching = { isSwitching },
                switchModeX = { switchModeX.floatValue }
            )
            SeekbarDuration(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                visible = { !isSwitching },
                text = { currentTimeText.value },
                textStyle = textStyle,
                offsetProgress = progress
            )
            SeekbarSwitcher(switching = { isSwitching })
        }
    }
}

@Composable
private fun SeekbarBackground(
    modifier: Modifier = Modifier,
    bgColor: Color = MaterialTheme.colorScheme.background,
    visible: () -> Boolean = { false }
) {
    val bgAlpha = animateFloatAsState(
        targetValue = if (visible()) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        visibilityThreshold = 0.001f,
        label = "SeekbarBackground_bgAlpha"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            color = bgColor,
            alpha = bgAlpha.value
        )
    }
}

@Composable
private fun SeekbarContentMask(
    modifier: Modifier = Modifier,
    maskColor: Color = Color(0x33646464),
    clip: () -> Boolean = { false }
) {
    val path = remember { Path() }
    val clipProgress = animateFloatAsState(
        targetValue = if (clip()) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        visibilityThreshold = 0.001f,
        label = "SeekbarContentMask_clipProgress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val maxPadding = 4.dp.toPx()
        val paddingValue = maxPadding * clipProgress.value

        val innerRadius = 16.dp.toPx() - paddingValue
        val innerHeight = size.height - (paddingValue * 2f)
        val innerWidth = size.width - (paddingValue * 2f)

        path.reset()
        path.addRoundRect(
            RoundRect(
                rect = Rect(
                    offset = Offset(x = paddingValue, y = paddingValue),
                    size = Size(width = innerWidth, height = innerHeight)
                ),
                cornerRadius = CornerRadius(innerRadius, innerRadius)
            )
        )

        clipPath(path) {
            drawRect(color = maskColor)
        }
    }
}

/**
 * 总时长展示。
 *
 * 时长未知时（在线内容加载中）不显示 `00:00`，也不显示占位符文本，而是换成一条呼吸的短条，
 * 明确表达"还不知道有多长"。两种形态之间用 [AnimatedContent] 过渡。
 */
@Composable
private fun SeekbarMaxDuration(
    modifier: Modifier = Modifier,
    visible: () -> Boolean = { true },
    textStyle: TextStyle,
    durationMs: () -> Long,
) {
    val alphaValue = animateFloatAsState(
        targetValue = if (visible()) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        visibilityThreshold = 0.001f,
        label = "SeekbarMaxDuration_alphaValue"
    )
    val duration = durationMs()

    AnimatedContent(
        modifier = modifier
            .graphicsLayer { alpha = alphaValue.value },
        targetState = duration > 0L,
        transitionSpec = {
            (fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterEnd,
        label = "SeekbarMaxDuration",
    ) { hasDuration ->
        if (hasDuration) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = duration.durationToTime(),
                style = textStyle.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.2f),
                        blurRadius = 5f
                    )
                )
            )
        } else {
            SeekbarDurationPlaceholder()
        }
    }
}

/** 未知总时长：一条呼吸的短条，代替 `00:00`。 */
@Composable
private fun SeekbarDurationPlaceholder(modifier: Modifier = Modifier) {
    val pulseAlpha by rememberInfiniteTransition(label = "SeekbarDurationPlaceholder")
        .animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "SeekbarDurationPlaceholder_alpha",
        )

    Canvas(
        modifier = modifier
            .padding(end = 16.dp)
            .size(width = 42.dp, height = 8.dp)
            .graphicsLayer { alpha = pulseAlpha }
    ) {
        drawRoundRect(
            color = Color.White,
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
    }
}

@Composable
private fun SeekbarDuration(
    modifier: Modifier = Modifier,
    text: () -> String,
    textStyle: TextStyle,
    visible: () -> Boolean = { true },
    offsetProgress: () -> Float = { 0f }
) {
    val accumulator = remember { AccumulatedValue() }
    val alphaValue = animateFloatAsState(
        targetValue = if (visible()) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        visibilityThreshold = 0.001f,
        label = "SeekbarDuration_alphaValue"
    )

    BoxWithConstraints(modifier = modifier.wrapContentSize()) {
        Text(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = alphaValue.value

                    val maxPadding = 4.dp.toPx()
                    val innerWidth = constraints.maxWidth - (maxPadding * 2f)

                    translationX =
                        ((innerWidth * offsetProgress()) - size.width - 32.dp.toPx())
                            .let { accumulator.accumulate(it).toFloat() }
                            .coerceAtLeast(0f)
                },
            text = text(),
            style = textStyle.copy(
                shadow = Shadow(
                    color = Color.Black.copy(0.2f),
                    blurRadius = 5f
                )
            )
        )
    }
}

@Composable
private fun SeekbarThumb(
    modifier: Modifier = Modifier,
    thumbColor: () -> Color = { Color(0xFF007AD5) },
    progress: () -> Float = { 1f },
    bufferedProgress: () -> Float = { 0f },
    clip: () -> Boolean = { false },
    switching: () -> Boolean = { false },
    switchModeX: () -> Float = { 0f }
) {
    val path = remember { Path() }
    val clipProgress = animateFloatAsState(
        targetValue = if (clip()) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        visibilityThreshold = 0.001f,
        label = "SeekbarThumb_clipProgress"
    )
    val switchProgress = animateFloatAsState(
        targetValue = if (switching()) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        visibilityThreshold = 0.001f,
        label = "SeekbarThumb_switchProgress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val maxPadding = 4.dp.toPx()
        val paddingValue = maxPadding * clipProgress.value

        val innerRadius = 16.dp.toPx() - paddingValue
        val innerHeight = size.height - (paddingValue * 2f)
        val innerWidth = size.width - (paddingValue * 2f)

        val thumbWidth = lerp(
            start = innerWidth * progress(),    // 根据进度计算的宽度
            stop = innerWidth / 3f,                 // 进度条均分宽度
            fraction = switchProgress.value      // 根据切换进度进行插值
        ).coerceIn(0f, innerWidth)

        val thumbLeft = lerp(
            start = paddingValue,
            stop = switchModeX() - (innerWidth / 3f) / 2f,
            fraction = switchProgress.value
        ).coerceIn(
            paddingValue,
            paddingValue + innerWidth - (innerWidth / 3f)
        )    // 限制滑块位置，确保其始终处于可见范围内

        path.reset()
        path.addRoundRect(
            RoundRect(
                rect = Rect(
                    offset = Offset(x = paddingValue, y = paddingValue),
                    size = Size(width = innerWidth, height = innerHeight)
                ),
                cornerRadius = CornerRadius(innerRadius, innerRadius)
            )
        )

        clipPath(path) {
            // 缓冲进度：与滑块同形，半透明垫在播放进度下面；
            // 切到播放模式选择器时随 switchProgress 一起淡出。
            val bufferedWidth = (innerWidth * bufferedProgress()).coerceIn(0f, innerWidth)
            val bufferedAlpha = 1f - switchProgress.value
            if (bufferedWidth > 0f && bufferedAlpha > 0.001f) {
                val color = thumbColor()
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * bufferedAlpha * 0.35f),
                    cornerRadius = CornerRadius(innerRadius, innerRadius),
                    topLeft = Offset(x = paddingValue, y = paddingValue),
                    size = Size(width = bufferedWidth, height = innerHeight)
                )
            }

            // 绘制滑块
            drawRoundRect(
                color = thumbColor(),
                cornerRadius = CornerRadius(innerRadius, innerRadius),
                topLeft = Offset(x = thumbLeft, y = paddingValue),
                size = Size(width = thumbWidth, height = innerHeight)
            )
        }
    }
}

@Composable
private fun SeekbarSwitcher(
    modifier: Modifier = Modifier,
    switching: () -> Boolean = { false },
) {
    AnimatedVisibility(
        modifier = modifier.fillMaxSize(),
        visible = switching(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Icon(
                imageVector = vectorResource(RemixIcon.Media.orderPlayFill),
                contentDescription = null,
                tint = Color.White
            )
            Icon(
                imageVector = vectorResource(RemixIcon.Media.repeatOneFill),
                contentDescription = null,
                tint = Color.White
            )
            Icon(
                imageVector = vectorResource(RemixIcon.Media.shuffleFill),
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

private fun Modifier.combineDetectDrag(
    key: Any = Unit,
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onLongClickStart: (Offset) -> Unit = {}
): Modifier = this
    .pointerInput(key) {
        detectDragGestures(
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
            onDrag = onDrag
        )
    }
    .pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                onLongClickStart(it)
                onDragStart(it)
            },
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
            onDrag = onDrag
        )
    }

private fun Long.durationToTime(): String {
    val hour = this / 3600000
    val minute = this / 60000 % 60
    val second = this / 1000 % 60
    return timeToString(hour, minute, second)
}

fun timeToString(hour: Number, minute: Number, second: Number): String {
    val hourText = hour.toLong().coerceAtLeast(0).let { if (it >= 10) it.toString() else "0$it" }
    val minuteText =
        minute.toLong().coerceAtLeast(0).let { if (it >= 10) it.toString() else "0$it" }
    val secondText =
        second.toLong().coerceAtLeast(0).let { if (it >= 10) it.toString() else "0$it" }

    return if (hour.toLong() > 0L) "$hourText:$minuteText:$secondText"
    else "$minuteText:$secondText"
}

@Composable
private fun durationToText(
    duration: () -> Long = { 0L }
): MutableState<String> {
    val durationText = remember { mutableStateOf(duration().durationToTime()) }

    LaunchedEffect(Unit) {
        var lastTime = -1L
        var hour = 0
        var minute = 0
        var second = 0

        snapshotFlow { duration() }
            .onEach { timeValue ->
                if (timeValue / 1000L != lastTime) {
                    val hourTemp = (timeValue / 3600000).toInt()
                    val minuteTemp = (timeValue / 60000 % 60).toInt()
                    val secondTemp = (timeValue / 1000 % 60).toInt()

                    if (hourTemp != hour || minuteTemp != minute || secondTemp != second) {
                        hour = hourTemp
                        minute = minuteTemp
                        second = secondTemp

                        durationText.value = timeToString(hour, minute, second)
                    }
                }
                lastTime = timeValue / 1000L
            }
            .launchIn(this)
    }

    return durationText
}
