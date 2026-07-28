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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.actualScreen

/**
 * 底部 Tab 导航栏组件
 */
@Composable
fun NavigateTabBar(
    modifier: Modifier = Modifier,
    currentScreen: () -> Screen?,
    tabScreens: () -> List<Screen>,
    onSelectTab: (Screen) -> Unit = {}
) {
    val defaultTitle = remember { "Unknown" }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabScreens().forEach { screen ->
            val screenInfo = (screen.actualScreen() as? ScreenInfoFactory)?.provideScreenInfo()
            val title = screenInfo?.title?.invoke() ?: defaultTitle
            val icon = screenInfo?.icon

            NavigateItem(
                modifier = Modifier.weight(1f),
                title = { title },
                icon = { icon },
                isSelected = { currentScreen()?.key == screen.key },
                onClick = { onSelectTab(screen) }
            )
        }
    }
}

/**
 * 导航项组件
 */
@Composable
fun NavigateItem(
    modifier: Modifier = Modifier,
    title: () -> String,
    icon: () -> ImageVector?,
    isSelected: () -> Boolean = { false },
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    baseColor: Color = MaterialTheme.colorScheme.primaryContainer,
    unSelectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
) {
    val iconTintColor = animateColorAsState(
        targetValue = if (isSelected()) baseColor else unSelectedColor,
        label = "iconTint"
    )

    Surface(
        color = Color.Transparent,
        onClick = onClick,
        shape = RectangleShape,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                icon()?.let {
                    Image(
                        imageVector = it,
                        contentDescription = title(),
                        colorFilter = ColorFilter.tint(iconTintColor.value),
                        contentScale = FixedScale(if (isSelected()) 1.1f else 1f)
                    )
                }
                AnimatedVisibility(visible = isSelected()) {
                    Text(
                        text = title(),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        letterSpacing = 0.1.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}
