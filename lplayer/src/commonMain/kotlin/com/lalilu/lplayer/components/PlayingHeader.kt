package com.lalilu.lplayer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlayingHeader(
    modifier: Modifier = Modifier,
    title: () -> String,
    subTitle: () -> String,
    contentColor: () -> Color,
    isPlaying: () -> Boolean,
) {
    val density = LocalDensity.current
    val slideMovement = remember { density.run { 50.dp.toPx().toInt() } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        AnimatedContent(
            targetState = title(),
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
            transitionSpec = {
                slideTransition(
                    duration = 400,
                    movement = slideMovement
                )
            },
            label = "TitleTextAnimation"
        ) { text ->
            Text(
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        spacing = MarqueeSpacing(30.dp)
                    ),
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
//            PlayingTipIcon(isPlaying = isPlaying)
            AnimatedContent(
                targetState = subTitle(),
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    slideTransition(
                        duration = 450,
                        movement = slideMovement
                    )
                },
                label = "SubTitleTextAnimation"
            ) { text ->
                Text(
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(30.dp)
                        ),
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor().copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun <T> AnimatedContentTransitionScope<T>.slideTransition(
    duration: Int = 500,
    movement: Int = 200,
    transitionPercent: Float = 0.6f,
    enterEasing: Easing = EaseOut,
    exitEasing: Easing = EaseIn,
    direction: AnimatedContentTransitionScope.SlideDirection = AnimatedContentTransitionScope.SlideDirection.Left,
): ContentTransform {
    return fadeIn(
        animationSpec = TweenSpec(
            durationMillis = (duration * (1f - transitionPercent)).toInt(),
            delay = (duration * transitionPercent).toInt(),
            easing = enterEasing
        ),
    ) + slideIntoContainer(
        towards = direction,
        animationSpec = TweenSpec(durationMillis = duration, easing = enterEasing),
        initialOffset = { movement }
    ) togetherWith fadeOut(
        animationSpec = TweenSpec(
            durationMillis = (duration * transitionPercent).toInt(),
            easing = exitEasing
        ),
    ) + slideOutOfContainer(
        towards = direction,
        animationSpec = TweenSpec(durationMillis = duration, easing = exitEasing),
        targetOffset = { -movement }
    )
}