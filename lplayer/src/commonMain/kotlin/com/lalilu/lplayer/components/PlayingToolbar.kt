package com.lalilu.lplayer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lalilu.lplayer.extensions.enableFor


@Composable
fun PlayingToolbar(
    modifier: Modifier = Modifier,
    title: () -> String,
    subtitle: () -> String,
    isPlaying: () -> Boolean = { false },
    isUserTouchEnable: () -> Boolean = { false },
    isExtraVisible: () -> Boolean = { true },
    contentColor: () -> Color,
    contentPadding: PaddingValues = PaddingValues(start = 25.dp, end = 20.dp),
    onClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    fixContent: @Composable RowScope.() -> Unit = {},
    extraContent: @Composable AnimatedVisibilityScope.() -> Unit = {}
) {
    val enter = remember {
        fadeIn(
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + expandHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            clip = false
        ) + slideInHorizontally(
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) { it / 2 }
    }
    val exit = remember {
        fadeOut(
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + shrinkHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            clip = false
        ) + slideOutHorizontally(
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) { it / 2 }
    }

    Row(
        modifier = modifier
            .enableFor(isUserTouchEnable) {
                // 单击与双击必须交给同一个识别器：
                // 两个 pointerInput 叠在同一节点上时，modifier 链尾的那个在 Main 阶段先派发，
                // clickable 会 consume() 掉按下事件，链首的双击识别器就永远等不到
                // 未消费的按下事件（awaitFirstDown(requireUnconsumed = true)），双击因此静默失效。
                // 传入 onDoubleClick 后，单击回调会延后到双击判定窗口结束，再触发。
                combinedClickable(
                    onClick = { if (isUserTouchEnable()) onClick() },
                    onDoubleClick = { if (isUserTouchEnable()) onDoubleClick() },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
            }
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayingHeader(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            title = title,
            subTitle = subtitle,
            contentColor = contentColor,
            isPlaying = isPlaying
        )

        fixContent()

        AnimatedVisibility(
            visible = isExtraVisible(),
            enter = enter,
            exit = exit,
            content = extraContent
        )
    }
}