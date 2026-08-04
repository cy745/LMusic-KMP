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
import com.materialkolor.DynamicMaterialTheme

val LocalFontFamily = staticCompositionLocalOf<FontFamily> { error("No font family provided") }
val LocalLyricFontFamily = staticCompositionLocalOf<FontFamily?> { null }
val LocalSeedColor = staticCompositionLocalOf<MutableState<Color>> { error("No seed color state provided") }

/**
 * 平台默认字体族：
 * - Android/iOS：系统字体（不打包字体，省包体积）
 * - JVM：附带可变字体（noto_sans_sc_vf）
 * - Web：默认字体（NotoSansSC-Regular 在 Web 阶段接入）
 */
@Composable
expect fun platformDefaultFontFamily(): FontFamily

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
    globalFontFamily: FontFamily? = null,
    lyricFontFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val seedColorState = remember { mutableStateOf(Color.Red) }
    val defaultFontFamily = platformDefaultFontFamily()
    val appliedGlobalFont = globalFontFamily ?: defaultFontFamily
    // 歌词字体未配置时使用平台默认字体，不跟随界面字体
    val appliedLyricFont = lyricFontFamily ?: defaultFontFamily

    CompositionLocalProvider(
        LocalSeedColor provides seedColorState,
        LocalFontFamily provides appliedGlobalFont,
        LocalLyricFontFamily provides appliedLyricFont
    ) {
        DynamicMaterialTheme(
            seedColor = seedColorState.value,
            typography = createTypography(MaterialTheme.typography, appliedGlobalFont),
            isDark = isDarkTheme,
            animate = true,
            content = content,
        )
    }
}
