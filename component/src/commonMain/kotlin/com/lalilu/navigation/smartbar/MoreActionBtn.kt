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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.vectorResource


/**
 * 更多操作按钮组件
 */
@Composable
internal fun MoreActionBtn(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    dotColors: List<Color> = emptyList(),
    onClick: () -> Unit = {}
) {
    TextButton(
        modifier = modifier.fillMaxHeight(),
        shape = RectangleShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        onClick = onClick
    ) {
        val showingColor = remember { mutableStateOf<Color?>(null) }

        LaunchedEffect(showingColor.value) {
            if (showingColor.value == null) {
                showingColor.value = dotColors.firstOrNull()
                return@LaunchedEffect
            }

            delay(3000)
            if (!isActive) return@LaunchedEffect

            val currentIndex = dotColors.indexOf(showingColor.value)
            val nextIndex = (currentIndex + 1) % dotColors.size
            showingColor.value = dotColors.getOrNull(nextIndex)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = vectorResource(RemixIcon.Arrows.arrowRightSLine),
                contentDescription = null,
                tint = color
            )

            showingColor.value?.let { dotColor ->
                AnimatedContent(
                    modifier = Modifier.align(Alignment.TopStart),
                    transitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessLow))
                    },
                    targetState = dotColor,
                    label = "dotColor"
                ) {
                    Spacer(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color = it)
                            .size(8.dp)
                    )
                }
            }
        }
    }
}
