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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.RemixIcon
import com.lalilu.extensions.ClassicBackHandler
import com.lalilu.navigation.ScreenBarFactory
import org.jetbrains.compose.resources.vectorResource


/**
 * 可取消的搜索输入栏。
 *
 * [cancellable] 为 `true` 时，返回操作会优先清空非空关键词；关键词为空时不拦截系统返回，
 * 左侧返回按钮则调用 [onDismiss]。为 `false` 时，返回操作直接关闭输入栏。
 */
@Composable
fun ScreenBarFactory.CancellableInputerBarPanel(
    focusOnShow: Boolean = true,
    cancellable: Boolean = false,
    isVisible: () -> Boolean,
    onDismiss: () -> Unit,
    keyword: () -> String,
    onUpdateKeyword: (String) -> Unit
) {
    RegisterContent(
        isVisible = isVisible,
        onBackPressed = if (cancellable) null else onDismiss
    ) {
        CancellableInputerBarPanelContent(
            modifier = Modifier,
            focusOnShow = focusOnShow,
            cancellable = cancellable,
            keyword = keyword,
            onUpdateKeyword = onUpdateKeyword,
            onClickBack = onDismiss
        )
    }
}

@Composable
private fun CancellableInputerBarPanelContent(
    modifier: Modifier = Modifier,
    focusOnShow: Boolean = true,
    cancellable: Boolean = false,
    keyword: () -> String,
    onUpdateKeyword: (String) -> Unit,
    onClickBack: (() -> Unit)? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    if (focusOnShow) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }

    if (cancellable) {
        ClassicBackHandler(enabled = keyword().isNotEmpty()) {
            onUpdateKeyword("")
        }
    }

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            modifier = Modifier.fillMaxHeight(),
            shape = RectangleShape,
            contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            onClick = {
                keyboard?.hide()

                when {
                    cancellable && keyword().isNotEmpty() -> {
                        onUpdateKeyword("")
                    }

                    onClickBack != null -> {
                        onClickBack()
                    }
                }
            }
        ) {
            Icon(
                imageVector = vectorResource(RemixIcon.Arrows.arrowLeftSLine),
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = null
            )
            Text(
                text = "关闭",
                fontSize = 14.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        BasicTextField(
            modifier = Modifier
                .focusRequester(focusRequester)
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.onBackground.copy(0.05f)),
            value = keyword(),
            onValueChange = onUpdateKeyword,
            singleLine = true,
            maxLines = 1,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            textStyle = TextStyle.Default.copy(
                fontSize = 16.sp,
                lineHeight = 16.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            ),
            decorationBox = { content ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    this@Row.AnimatedVisibility(
                        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                        visible = keyword().isEmpty()
                    ) {
                        Text(
                            modifier = Modifier.padding(start = 2.dp),
                            text = "输入关键词以匹配元素",
                            fontSize = 14.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.3f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            content()
                        }

                        AnimatedVisibility(
                            enter = fadeIn() + scaleIn(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                ),
                                initialScale = 0f
                            ),
                            exit = fadeOut() + scaleOut(
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                targetScale = 0f
                            ),
                            visible = keyword().isNotEmpty()
                        ) {
                            IconButton(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                                onClick = { onUpdateKeyword("") }
                            ) {
                                Icon(
                                    imageVector = vectorResource(RemixIcon.System.closeLine),
                                    contentDescription = "clear",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
