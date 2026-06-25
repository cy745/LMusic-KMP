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

package com.lalilu.lmusic.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.WindowWrapper
import com.lalilu.atLeastMedium
import com.lalilu.krouter.KRouter
import com.lalilu.lmusic.kRouterInjectMap
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.ScreenWrapper
import com.lalilu.preview.preview

sealed interface NavSidebarItem {
    data class NavSection(
        val title: String,
        val screens: List<Screen>
    ) : NavSidebarItem

    data class NavSingleItem(
        val screen: Screen
    ) : NavSidebarItem

    data object Divider : NavSidebarItem
}


@Composable
fun NavSideApplier(
    modifier: Modifier = Modifier,
    sidebarModifier: Modifier = Modifier,
    items: List<NavSidebarItem> = emptyList(),
    isSelected: (Screen) -> Boolean = { false },
    onSelectScreen: (Screen?) -> Unit = {},
    content: @Composable () -> Unit
) {
    Row(modifier = modifier.fillMaxSize()) {
        val windowClass = currentWindowAdaptiveInfo().windowSizeClass
        AnimatedVisibility(
            visible = windowClass.atLeastMedium()
        ) {
            NavSidebar(
                modifier = sidebarModifier,
                items = items,
                isSelected = isSelected,
                onSelectScreen = onSelectScreen
            )
        }

        WindowWrapper(
            modifier = Modifier.fillMaxWidth()
                .weight(1f)
        ) {
            content()
        }
    }
}

@Composable
fun NavSidebar(
    modifier: Modifier = Modifier,
    items: List<NavSidebarItem> = emptyList(),
    isSelected: (Screen) -> Boolean = { false },
    onSelectScreen: (Screen?) -> Unit = {}
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues()

    LazyColumn(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .background(MaterialTheme.colorScheme.onBackground.copy(0.1f)),
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding() + 16.dp,
            bottom = 16.dp
        )
    ) {
        items.forEach {
            when (it) {
                is NavSidebarItem.NavSection -> {
                    // Section Title
                    item {
                        Text(
                            text = it.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Section Items
                    items(items = it.screens) { screen ->
                        val screenInfo = getScreenInfo(screen)

                        NavSidebarItem(
                            title = screenInfo.title(),
                            icon = screenInfo.icon,
                            isSelected = isSelected(screen),
                            onClick = { onSelectScreen(screen) }
                        )
                    }
                }

                is NavSidebarItem.Divider -> {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 1.dp
                        )
                    }
                }

                is NavSidebarItem.NavSingleItem -> {
                    item {
                        val screenInfo = getScreenInfo(it.screen)

                        NavSidebarItem(
                            title = screenInfo.title(),
                            icon = screenInfo.icon,
                            isSelected = isSelected(it.screen),
                            onClick = { onSelectScreen(it.screen) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavSidebarItem(
    title: String,
    icon: ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            ),
            color = contentColor
        )
    }
}

@Composable
private fun getScreenInfo(screen: Screen): ScreenInfo {
    var instance = screen
    if (instance is ScreenWrapper) {
        instance = instance.screen
    }

    if (instance is ScreenInfoFactory) {
        return instance.provideScreenInfo()
    }

    return ScreenInfo(
        title = { getScreenTitle(instance) },
        icon = null
    )
}

private fun getScreenTitle(screen: Screen): String {
    val className = screen::class.qualifiedName ?: "Unknown"
    return className.substringAfterLast(".")
}

@Preview
@Composable
private fun NavSidebarPreview() = preview {
    KRouter.init(kRouterInjectMap()::getMap)
    NavSidebar()
}

private fun getScreen(route: String) = KRouter.route<Screen>(route) ?: ExceptionScreen.SCREEN_NOT_FOUND