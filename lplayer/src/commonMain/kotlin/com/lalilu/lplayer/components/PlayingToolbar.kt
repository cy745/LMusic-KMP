package com.lalilu.lplayer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
    onClick: () -> Unit = {},
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
                clickable(
                    onClick = { if (isUserTouchEnable()) onClick() },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
            }
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = 25.dp, end = 20.dp),
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