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

package com.lalilu.llyricview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.common.kv.KVContext
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.settingsGroup
import com.lalilu.navigation.AppRouter
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

/**
 * 歌词样式设置分组。
 *
 * `LyricSettings` 关闭了自动保存（disableAutoSave），
 * 所有写入都必须显式 [com.lalilu.common.kv.UpdatableKV.save]，
 * 否则设置不会持久化（旧版 bug 的根源）。
 */
@Factory
@Named("settings_lyric")
fun provideLyricSettings(
    includeFontEntry: Boolean = true,
): SettingsGroup {
    val settings = KVContext.obtainStatic<LyricSettings>(
        key = "LyricSettings",
        defaultValue = LyricSettings(),
    ).apply { disableAutoSave() }

    fun update(block: LyricSettings.() -> LyricSettings) {
        settings.value = settings.value.block()
        settings.save()
    }

    return settingsGroup(
        key = "lyric",
        order = 12,
        title = { "歌词" },
        description = { "歌词显示样式" },
    ) {
        dropdown(
            key = "lyric_text_align",
            title = { "对齐方式" },
            selectedValue = settings.value.textAlign,
            options = listOf(TextAlign.Start, TextAlign.Center, TextAlign.End),
            optionLabel = { align ->
                when (align) {
                    TextAlign.Start -> "左对齐"
                    TextAlign.Center -> "居中"
                    else -> "右对齐"
                }
            },
            onValueChange = { update { copy(textAlign = it) } },
            serialize = { it.toString() },
            deserialize = { name ->
                when (name) {
                    "Center" -> TextAlign.Center
                    "End" -> TextAlign.End
                    else -> TextAlign.Start
                }
            }
        )

        slider(
            key = "lyric_main_font_size",
            title = { "主歌词字号" },
            value = settings.value.mainFontSize.value,
            onValueChange = { update { copy(mainFontSize = it.sp) } },
            valueRange = 20f..72f,
            valueLabel = { "${it.toInt()} sp" }
        )
        slider(
            key = "lyric_main_line_height",
            title = { "主歌词行高" },
            value = settings.value.mainLineHeight.value,
            onValueChange = { update { copy(mainLineHeight = it.sp) } },
            valueRange = 16f..100f,
            valueLabel = { "${it.toInt()} sp" }
        )
        slider(
            key = "lyric_main_font_weight",
            title = { "主歌词字重" },
            value = settings.value.mainFontWeight.toFloat(),
            onValueChange = { update { copy(mainFontWeight = it.toInt()) } },
            valueRange = 100f..900f,
            steps = 7,
            valueLabel = { it.toInt().toString() }
        )

        slider(
            key = "lyric_translation_font_size",
            title = { "翻译字号" },
            value = settings.value.translationFontSize.value,
            onValueChange = { update { copy(translationFontSize = it.sp) } },
            valueRange = 10f..40f,
            valueLabel = { "${it.toInt()} sp" }
        )
        slider(
            key = "lyric_translation_line_height",
            title = { "翻译行高" },
            value = settings.value.translationLineHeight.value,
            onValueChange = { update { copy(translationLineHeight = it.sp) } },
            valueRange = 16f..64f,
            valueLabel = { "${it.toInt()} sp" }
        )
        slider(
            key = "lyric_translation_font_weight",
            title = { "翻译字重" },
            value = settings.value.translationFontWeight.toFloat(),
            onValueChange = { update { copy(translationFontWeight = it.toInt()) } },
            valueRange = 100f..900f,
            steps = 7,
            valueLabel = { it.toInt().toString() }
        )

        slider(
            key = "lyric_container_padding_h",
            title = { "水平边距" },
            value = settings.value.containerPadding
                .calculateLeftPadding(LayoutDirection.Ltr)
                .value,
            onValueChange = {
                update {
                    copy(containerPadding = PaddingValues(horizontal = it.dp, vertical = 15.dp))
                }
            },
            valueRange = 0f..80f,
            valueLabel = { "${it.toInt()} dp" }
        )
        slider(
            key = "lyric_gap_size",
            title = { "歌词间距" },
            value = settings.value.gapSize.value,
            onValueChange = { update { copy(gapSize = it.dp) } },
            valueRange = 0f..24f,
            valueLabel = { "${it.toInt()} dp" }
        )
        slider(
            key = "lyric_time_offset",
            title = { "时间偏移" },
            value = settings.value.timeOffset.toFloat(),
            onValueChange = { update { copy(timeOffset = it.toLong()) } },
            valueRange = 0f..500f,
            valueLabel = { "${it.toInt()} ms" }
        )

        slider(
            key = "lyric_scroll_spring_damping",
            title = { "滚动阻尼" },
            value = settings.value.scrollSpringDampingRatio,
            onValueChange = { update { copy(scrollSpringDampingRatio = it) } },
            valueRange = 0.1f..1f,
            valueLabel = { it.toString().take(4) }
        )
        slider(
            key = "lyric_scroll_spring_stiffness",
            title = { "滚动刚度" },
            value = settings.value.scrollSpringStiffness,
            onValueChange = { update { copy(scrollSpringStiffness = it) } },
            valueRange = 50f..400f,
            valueLabel = { it.toInt().toString() }
        )

        switch(
            key = "lyric_blur_effect",
            title = { "歌词模糊效果" },
            value = settings.value.blurEffectEnable,
            onValueChange = { update { copy(blurEffectEnable = it) } }
        )
        switch(
            key = "lyric_only_current_translation",
            title = { "仅显示当前翻译" },
            value = settings.value.onlyCurrentTranslationVisible,
            onValueChange = { update { copy(onlyCurrentTranslationVisible = it) } }
        )
        switch(
            key = "lyric_translation_visible",
            title = { "显示翻译" },
            value = settings.value.translationVisible,
            onValueChange = { update { copy(translationVisible = it) } }
        )

        if (includeFontEntry) {
            click(
                key = "lyric_font",
                title = { "歌词字体" },
                summary = { "在字体管理中配置" },
                onClick = {
                    AppRouter.route("/settings/fonts").jump()
                }
            )
        }
    }
}
