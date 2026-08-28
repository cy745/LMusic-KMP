/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.navigation.smartbar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.component.LongClickableTextButton
import com.lalilu.navigation.ActionContext
import com.lalilu.navigation.ScreenAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * 操作项组件，根据 action 类型渲染不同的 UI
 */
@Composable
internal fun ActionItem(
    modifier: Modifier = Modifier,
    actionContext: ActionContext,
    action: ScreenAction
) {
    when (action) {
        is ScreenAction.DeepLink -> Unit

        is ScreenAction.Dynamic -> {
            action.content(actionContext)
        }

        is ScreenAction.Static -> {
            if (action.longClick(actionContext)) {
                LongClickActionItemContent(
                    modifier = modifier,
                    color = action.color(),
                    title = action.title(),
                    subTitle = action.subTitle(),
                    icon = action.icon(),
                    dotColor = action.dotColor(),
                    onAction = { action.onAction(actionContext) }
                )
            } else {
                ActionItemContent(
                    modifier = modifier,
                    color = action.color(),
                    title = action.title(),
                    subTitle = action.subTitle(),
                    icon = action.icon(),
                    dotColor = action.dotColor(),
                    onAction = { action.onAction(actionContext) }
                )
            }
        }
    }
}

/**
 * 长按操作项内容
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@Composable
fun LongClickActionItemContent(
    modifier: Modifier = Modifier,
    color: Color,
    title: String,
    subTitle: String? = null,
    icon: DrawableResource? = null,
    dotColor: Color? = null,
    fullyExpended: Boolean = false,
    onAction: () -> Unit = {}
) {
    val tipsShow = remember { mutableLongStateOf(0L) }

    LaunchedEffect(tipsShow.longValue) {
        delay(3000)

        if (!isActive) return@LaunchedEffect
        tipsShow.longValue = 0L
    }

    LongClickableTextButton(
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        horizontalArrangement = Arrangement.Start,
        contentPadding = PaddingValues(0.dp),
        onClick = { tipsShow.longValue = Clock.System.now().toEpochMilliseconds() },
        onLongClick = {
            tipsShow.longValue = 0
            onAction()
        }
    ) {
        Box(
            modifier = Modifier,
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Image(
                        modifier = Modifier.size(20.dp),
                        imageVector = vectorResource(icon),
                        contentDescription = title,
                        colorFilter = ColorFilter.tint(color = color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column(
                    modifier = Modifier,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )

                    if (fullyExpended && subTitle != null) {
                        AnimatedContent(
                            targetState = tipsShow.longValue > 0,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = ""
                        ) { show ->
                            if (show) {
                                Text(
                                    modifier = Modifier.alpha(0.6f),
                                    text = "长按以执行",
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            } else {
                                Text(
                                    text = subTitle,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    color = color.copy(0.5f),
                                )
                            }
                        }
                    } else {
                        AnimatedVisibility(visible = tipsShow.longValue > 0) {
                            Text(
                                modifier = Modifier.alpha(0.6f),
                                text = subTitle ?: "长按以执行",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }

            if (dotColor != null) {
                val animation = rememberInfiniteTransition(label = "")
                val scaleValue = animation.animateFloat(
                    initialValue = 0.1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(
                            offsetMillis = remember { Random.nextInt(0..1000) }
                        )
                    ),
                    label = ""
                )

                Spacer(
                    modifier = Modifier
                        .graphicsLayer { alpha = scaleValue.value }
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .clip(CircleShape)
                        .background(color = dotColor)
                        .size(8.dp)
                )
            }
        }
    }
}

/**
 * 普通操作项内容
 */
@Composable
fun ActionItemContent(
    modifier: Modifier = Modifier,
    color: Color,
    title: String,
    subTitle: String? = null,
    icon: DrawableResource? = null,
    dotColor: Color? = null,
    onAction: () -> Unit = {}
) {
    val contentColor = color.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.onBackground

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.2f),
        onClick = onAction
    ) {
        Box(
            modifier = Modifier,
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Image(
                        modifier = Modifier.size(20.dp),
                        imageVector = vectorResource(icon),
                        contentDescription = title,
                        colorFilter = ColorFilter.tint(color = color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column(
                    modifier = Modifier,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        color = contentColor,
                        fontWeight = FontWeight.Medium
                    )

                    if (subTitle != null) {
                        Text(
                            text = subTitle,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            color = contentColor.copy(0.5f),
                        )
                    }
                }
            }

            dotColor?.let { dot ->
                ActionDotIndicator(
                    color = dot,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                )
            }
        }
    }
}

/**
 * 操作项上的指示点动画
 */
@Composable
private fun ActionDotIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val animation = rememberInfiniteTransition(label = "dot")
    val scaleValue = animation.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = remember { Random.nextInt(0, 1000) }
            )
        ),
        label = "scale"
    )

    Spacer(
        modifier = modifier
            .graphicsLayer { alpha = scaleValue.value }
            .clip(CircleShape)
            .background(color = color)
            .size(8.dp)
    )
}
