/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lalilu.lhome.screen.songs

import androidx.annotation.MainThread
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.lalilu.RemixIcon
import com.lalilu.navigation.ScreenBarFactory
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.System
import com.lalilu.remixicon.arrows.arrowLeftSLine
import com.lalilu.remixicon.system.closeLine

@Composable
fun ScreenBarFactory.SongsSearcherPanel(
    isVisible: () -> Boolean,
    onDismiss: () -> Unit,
    keyword: () -> String,
    onUpdateKeyword: (String) -> Unit
) {
    RegisterContent(
        isVisible = isVisible,
        onDismiss = onDismiss,
        onBackPressed = { }
    ) {
        SongsSearcherPanelContent(
            modifier = Modifier,
            keyword = keyword,
            onUpdateKeyword = onUpdateKeyword,
            onBackPress = { onDismiss() }
        )
    }
}

@Composable
private fun SongsSearcherPanelContent(
    modifier: Modifier = Modifier,
    keyword: () -> String,
    onUpdateKeyword: (String) -> Unit,
    onBackPress: (() -> Unit)? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val dispatcherOwner = LocalNavigationEventDispatcherOwner.current
    val onBackPressedDispatcher = remember {
        val input = object : NavigationEventInput() {
            @MainThread
            fun onBackPress() {
                dispatchOnBackStarted(NavigationEvent())
                dispatchOnBackCompleted()
            }
        }.also { dispatcherOwner?.navigationEventDispatcher?.addInput(it) }

        input::onBackPress
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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

                if (onBackPress != null) {
                    onBackPress()
                } else {
                    onBackPressedDispatcher.invoke()
                }
            }
        ) {
            Icon(
                imageVector = RemixIcon.Arrows.arrowLeftSLine,
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
                                    imageVector = RemixIcon.System.closeLine,
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
