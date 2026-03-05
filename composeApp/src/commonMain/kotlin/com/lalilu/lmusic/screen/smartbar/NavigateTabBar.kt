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

package com.lalilu.lmusic.screen.smartbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfoFactory

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
        modifier = modifier
            .height(52.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabScreens().forEach { screen ->
            val screenInfo = (screen as? ScreenInfoFactory)?.provideScreenInfo()
            val title = screenInfo?.title?.invoke() ?: defaultTitle
            val icon = screenInfo?.icon

            NavigateItem(
                modifier = Modifier.weight(1f),
                title = { title },
                icon = { icon },
                isSelected = { currentScreen() === screen },
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
    baseColor: Color = MaterialTheme.colorScheme.primary,
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
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
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
