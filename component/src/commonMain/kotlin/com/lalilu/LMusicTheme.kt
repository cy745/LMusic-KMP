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
package com.lalilu

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lalilu.component.component.generated.resources.Res
import com.lalilu.component.component.generated.resources.noto_sans_sc_vf
import com.materialkolor.DynamicMaterialTheme
import org.jetbrains.compose.resources.Font

val LocalFontFamily = staticCompositionLocalOf<FontFamily> { error("No font family provided") }
val LocalSeedColor = staticCompositionLocalOf<MutableState<Color>> { error("No seed color state provided") }

/**
 * 创建一个新的 Typography 实例，基于父级 Typography 并应用指定的字体族。
 *
 * 此函数使用 remember 来缓存结果，确保在相同的 parent 和 fontFamily 参数下不会重复计算。
 *
 * @param parent 原始的 Typography 实例，作为基础样式。
 * @param fontFamily 要应用到所有文本样式的字体族。
 * @return 一个新的 Typography 实例，其中所有文本样式都使用了指定的字体族。
 */
@Composable
internal fun createTypography(
    parent: Typography,
    fontFamily: FontFamily
): Typography = remember(parent, fontFamily) {
    Typography(
        parent.displayLarge.copy(fontFamily = fontFamily),
        parent.displayMedium.copy(fontFamily = fontFamily),
        parent.displaySmall.copy(fontFamily = fontFamily),
        parent.headlineLarge.copy(fontFamily = fontFamily),
        parent.headlineMedium.copy(fontFamily = fontFamily),
        parent.headlineSmall.copy(fontFamily = fontFamily),
        parent.titleLarge.copy(fontFamily = fontFamily),
        parent.titleMedium.copy(fontFamily = fontFamily),
        parent.titleSmall.copy(fontFamily = fontFamily),
        parent.bodyLarge.copy(fontFamily = fontFamily),
        parent.bodyMedium.copy(fontFamily = fontFamily),
        parent.bodySmall.copy(fontFamily = fontFamily),
        parent.labelLarge.copy(fontFamily = fontFamily),
        parent.labelMedium.copy(fontFamily = fontFamily),
        parent.labelSmall.copy(fontFamily = fontFamily)
    )
}

@Composable
fun LMusicTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val seedColorState = remember { mutableStateOf(Color.Red) }
    val fontWeight = remember { (100..900 step 100).map { FontWeight(it) } }
    val fonts = fontWeight.map { Font(resource = Res.font.noto_sans_sc_vf, weight = it) }
    val fontFamily = remember { FontFamily(fonts) }

    CompositionLocalProvider(
        LocalSeedColor provides seedColorState,
        LocalFontFamily provides fontFamily
    ) {
        DynamicMaterialTheme(
            seedColor = seedColorState.value,
            typography = createTypography(MaterialTheme.typography, fontFamily),
            isDark = isDarkTheme,
            animate = true,
            content = content,
        )
    }
}