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

package com.lalilu.lmusic.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.settingsGroup
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.NavigatorHeader
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named
import kotlin.math.roundToInt

private val CornerOutsideColor = Color(0xFFFF684F)
private val CornerBorderLight = Color(0xFF006989)
private val CornerBorderDark = Color(0xFF59D9FF)

/** 全局设置页只暴露一个入口，校准交互全部留在独立页面。 */
@Factory
@Named("settings_display_corner")
fun provideDisplayCornerSettingsEntry(): SettingsGroup = settingsGroup(
    key = "display_corner",
    order = 5,
    title = { "显示与布局" },
    description = { "屏幕边缘与界面适配" },
) {
    click(
        key = "display_corner.calibrate",
        title = { "屏幕圆角校准" },
        summary = {
            val config = DisplayCornerSettingsStore.settings.value
            val system = rememberSystemDisplayCornerRadii()
            val effective = resolveDisplayCornerRadii(config, system)
            when {
                config.manualRadiusDp != null ->
                    "手动 ${effective.representativeTopRadius().dpLabel()}"

                system != null ->
                    "自动 · 左 ${effective.topLeftDp.dpLabel()} · 右 ${effective.topRightDp.dpLabel()}"

                else -> "自动检测不可用 · 默认 ${DEFAULT_DISPLAY_CORNER_RADIUS_DP.dpLabel()}"
            }
        },
        onClick = {
            AppRouter.route("/settings/display-corner").jump()
        },
    )
}

@Destination("/settings/display-corner")
data object DisplayCornerSettingsScreen : Screen, ScreenInfoFactory {
    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "屏幕圆角校准" },
            icon = RemixIcon.System.settings2Line,
        )
    }

    @Composable
    override fun Content() {
        DisplayCornerSettingsContent()
    }
}

@Composable
private fun DisplayCornerSettingsContent() {
    val config = DisplayCornerSettingsStore.settings.value
    val systemRadii = rememberSystemDisplayCornerRadii()
    val effectiveRadii = resolveDisplayCornerRadii(config, systemRadii)
    val sliderValue = effectiveRadii.representativeTopRadius()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = smartBarHeight() + 24.dp),
    ) {
        item(key = "corner_preview") {
            DisplayCornerPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                radii = effectiveRadii,
            )
        }

        item(key = "corner_header") {
            NavigatorHeader(
                title = "屏幕圆角校准",
                subTitle = "让预览紧贴设备边缘，调整播放器缩放时的圆角",
                paddingValues = PaddingValues(
                    top = 24.dp,
                    bottom = 12.dp,
                    start = 20.dp,
                    end = 20.dp,
                ),
            )
        }

        item(key = "corner_control") {
            CornerRadiusControl(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                value = sliderValue,
                config = config,
                systemRadii = systemRadii,
                onValueChange = DisplayCornerSettingsStore::updateManualRadius,
                onValueChangeFinished = DisplayCornerSettingsStore::persist,
                onUseAutomatic = DisplayCornerSettingsStore::useSystemRadius,
            )
        }
    }
}

@Composable
private fun DisplayCornerPreview(
    radii: SystemDisplayCornerRadii,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        CornerBorderDark
    } else {
        CornerBorderLight
    }
    val shape = remember(radii) {
        AbsoluteRoundedCornerShape(
            topLeft = radii.topLeftDp.dp,
            topRight = radii.topRightDp.dp,
            bottomRight = 0.dp,
            bottomLeft = 0.dp,
        )
    }

    Box(
        modifier = modifier.background(CornerOutsideColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.background)
                .border(width = 2.dp, color = borderColor, shape = shape)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CornerCalibrationGuide(
                modifier = Modifier.size(width = 148.dp, height = 72.dp),
                borderColor = borderColor,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "让珊瑚色完全消失",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "同时让青蓝色内框尽可能完整",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun CornerCalibrationGuide(
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val radius = 22.dp.toPx().coerceAtMost(size.height / 2f)
        val inset = strokeWidth
        val path = Path().apply {
            moveTo(inset, size.height)
            lineTo(inset, radius)
            quadraticTo(inset, inset, radius, inset)
            lineTo(size.width - radius, inset)
            quadraticTo(size.width - inset, inset, size.width - inset, radius)
            lineTo(size.width - inset, size.height)
        }
        drawPath(
            path = path,
            color = borderColor,
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = CornerOutsideColor,
            radius = 4.dp.toPx(),
            center = Offset(radius * 0.48f, radius * 0.48f),
        )
        drawCircle(
            color = CornerOutsideColor,
            radius = 4.dp.toPx(),
            center = Offset(size.width - radius * 0.48f, radius * 0.48f),
        )
    }
}

@Composable
private fun CornerRadiusControl(
    value: Float,
    config: DisplayCornerSettings,
    systemRadii: SystemDisplayCornerRadii?,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onUseAutomatic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "圆角半径",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            config.manualRadiusDp != null -> "当前使用手动覆盖值"
                            systemRadii != null -> "当前使用系统检测值"
                            else -> "当前平台无法检测，使用默认值"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = value.dpLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Slider(
                modifier = Modifier.padding(top = 8.dp),
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = 0f..MAX_DISPLAY_CORNER_RADIUS_DP,
            )

            Text(
                text = "拖动按 0.5dp 调整。数值过大时顶部会露出珊瑚色；数值过小时，青蓝色内框会被设备边缘截断。",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(
                visible = config.manualRadiusDp != null,
                modifier = Modifier.align(Alignment.End),
                enter = fadeIn(tween(durationMillis = 180)) +
                    expandVertically(
                        animationSpec = tween(durationMillis = 180),
                        expandFrom = Alignment.Top,
                    ),
                exit = fadeOut(tween(durationMillis = 150)) +
                    shrinkVertically(
                        animationSpec = tween(durationMillis = 180),
                        shrinkTowards = Alignment.Top,
                    ),
            ) {
                Box(
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    TextButton(onClick = onUseAutomatic) {
                        Text(
                            if (systemRadii != null) "恢复系统检测值"
                            else "恢复默认 ${DEFAULT_DISPLAY_CORNER_RADIUS_DP.dpLabel()}"
                        )
                    }
                }
            }
        }
    }
}

private fun Float.dpLabel(): String {
    val halfSteps = (this * 2f).roundToInt()
    val decimal = if (halfSteps % 2 == 0) "0" else "5"
    return "${halfSteps / 2}.$decimal dp"
}
