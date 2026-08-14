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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.RemixIcon
import com.lalilu.common.kv.KVContext
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.settingsGroup
import com.lalilu.lfont.manager.FontManager
import com.lalilu.navigation.AppRouter
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

/**
 * 歌词样式设置分组。
 *
 * `LyricSettings` 关闭了自动保存（disableAutoSave），
 * 所有写入都必须显式 [com.lalilu.common.kv.UpdatableKV.save]，
 * 否则设置不会持久化（旧版 bug 的根源）。
 *
 * 保存策略（与旧项目一致）：
 * - slider：拖动中实时写内存 state（歌词渲染即时预览），松手时才 save() 落盘
 * - dropdown / switch：选择即更新内存并 save()
 *
 * 分组结构对齐旧项目 LyricViewToolbar 的 Accordion：
 * 歌词方向 / 歌词样式 / 翻译样式 / 其他（偏移、滚动参数、开关、字体入口）。
 */
/**
 * 完整歌词设置组：供歌词设置子页 `/settings/lyric` 渲染。
 *
 * **不再通过 `@Factory` + `@Named` 自动注册进主设置页**（避免 17 项设置
 * 全部堆积到主设置页）；主设置页只保留 [provideLyricSettingsEntry] 的
 * click 入口，点击后跳转到子页。播放页弹窗使用 [provideLyricSettingsQuick]。
 *
 * @param includeFontEntry 是否包含「歌词字体」入口（子页 true，弹窗 false）
 */
fun provideLyricSettings(
    fontManager: FontManager,
    includeFontEntry: Boolean = true,
): SettingsGroup {
    val settings = KVContext.obtainStatic<LyricSettings>(
        key = "LyricSettings",
        defaultValue = LyricSettings(),
    ).apply { disableAutoSave() }

    /** 仅更新内存 state（驱动歌词渲染实时反馈），不落盘。 */
    fun updateInMemory(block: LyricSettings.() -> LyricSettings) {
        settings.value = settings.value.block()
    }

    /** 强制落盘。 */
    fun persist() = settings.save()

    /** 内存 + 落盘一步完成（dropdown / switch 等离散选择场景）。 */
    fun updateAndPersist(block: LyricSettings.() -> LyricSettings) {
        updateInMemory(block)
        persist()
    }

    return settingsGroup(
        key = "lyric",
        order = 12,
        title = { "歌词" },
        description = { "歌词显示样式" },
    ) {
        accordion(
            key = "lyric_align",
            title = { "歌词方向" },
            summary = {
                when (settings.value.textAlign) {
                    TextAlign.Start -> "当前: 左对齐"
                    TextAlign.Center -> "当前: 居中"
                    else -> "当前: 右对齐"
                }
            },
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
                onValueChange = { updateAndPersist { copy(textAlign = it) } },
                serialize = { it.toString() },
                deserialize = { name ->
                    when (name) {
                        "Center" -> TextAlign.Center
                        "End" -> TextAlign.End
                        else -> TextAlign.Start
                    }
                },
                optionIcon = { align ->
                    when (align) {
                        TextAlign.Start -> RemixIcon.Editor.alignLeft
                        TextAlign.Center -> RemixIcon.Editor.alignCenter
                        else -> RemixIcon.Editor.alignRight
                    }
                }
            )
        }

        accordion(
            key = "lyric_style",
            title = { "歌词样式" },
            summary = { "字号、行高、字重、边距" },
        ) {
            slider(
                key = "lyric_main_font_size",
                title = { "歌词文字大小" },
                value = settings.value.mainFontSize.value,
                onValueChange = { updateInMemory { copy(mainFontSize = it.sp) } },
                valueRange = 14f..64f,
                valueLabel = { "${it.toInt()} sp" },
                onValueChangeFinished = { persist() }
            )
            slider(
                key = "lyric_main_line_height",
                title = { "歌词行高大小" },
                value = settings.value.mainLineHeight.value,
                onValueChange = { updateInMemory { copy(mainLineHeight = it.sp) } },
                valueRange = 14f..72f,
                valueLabel = { "${it.toInt()} sp" },
                onValueChangeFinished = { persist() }
            )
            slider(
                key = "lyric_main_font_weight",
                title = { "歌词字重" },
                value = settings.value.mainFontWeight.toFloat(),
                onValueChange = { updateInMemory { copy(mainFontWeight = it.toInt()) } },
                valueRange = 50f..900f,
                steps = 16,
                valueLabel = { it.toInt().toString() },
                onValueChangeFinished = { persist() }
            )
            slider(
                key = "lyric_container_padding_h",
                title = { "横向边距" },
                value = settings.value.containerPadding
                    .calculateLeftPadding(LayoutDirection.Ltr)
                    .value,
                onValueChange = {
                    updateInMemory {
                        copy(containerPadding = PaddingValues(horizontal = it.dp, vertical = 15.dp))
                    }
                },
                valueRange = 0f..50f,
                valueLabel = { "${it.toInt()} dp" },
                onValueChangeFinished = { persist() }
            )
        }

        accordion(
            key = "lyric_translation_style",
            title = { "翻译样式" },
            summary = { "翻译字号、行高、字重、间距" },
        ) {
            slider(
                key = "lyric_translation_font_size",
                title = { "翻译文字大小" },
                value = settings.value.translationFontSize.value,
                onValueChange = { updateInMemory { copy(translationFontSize = it.sp) } },
                valueRange = 14f..64f,
                valueLabel = { "${it.toInt()} sp" },
                onValueChangeFinished = { persist() }
            )
            slider(
                key = "lyric_translation_line_height",
                title = { "翻译行高大小" },
                value = settings.value.translationLineHeight.value,
                onValueChange = { updateInMemory { copy(translationLineHeight = it.sp) } },
                valueRange = 14f..72f,
                valueLabel = { "${it.toInt()} sp" },
                onValueChangeFinished = { persist() }
            )
            slider(
                key = "lyric_translation_font_weight",
                title = { "翻译字重" },
                value = settings.value.translationFontWeight.toFloat(),
                onValueChange = { updateInMemory { copy(translationFontWeight = it.toInt()) } },
                valueRange = 50f..900f,
                steps = 16,
                valueLabel = { it.toInt().toString() },
                onValueChangeFinished = { persist() }
            )
            slider(
                key = "lyric_gap_size",
                title = { "歌词翻译间距" },
                value = settings.value.gapSize.value,
                onValueChange = { updateInMemory { copy(gapSize = it.dp) } },
                valueRange = 0f..50f,
                valueLabel = { "${it.toInt()} dp" },
                onValueChangeFinished = { persist() }
            )
        }

        accordion(
            key = "lyric_misc",
            title = { "其他" },
            summary = { "偏移、滚动参数与显示开关" },
        ) {
            // TODO(#6): 歌词播放偏移校准落地后，timeOffset 将与 #6 的音游式节拍校准联动
            slider(
                key = "lyric_time_offset",
                title = { "歌词偏移时间" },
                value = settings.value.timeOffset.toFloat(),
                onValueChange = { updateInMemory { copy(timeOffset = it.toLong()) } },
                valueRange = -200f..500f,
                valueLabel = { "${it.toInt()} ms" },
                summary = { "适合使用蓝牙耳机时针对耳机延迟调节" },
                onValueChangeFinished = { persist() }
            )
            slider(
                key = "lyric_scroll_spring_damping",
                title = { "歌词滚动阻尼（默认0.75）" },
                value = settings.value.scrollSpringDampingRatio,
                onValueChange = { updateInMemory { copy(scrollSpringDampingRatio = it) } },
                valueRange = 0.3f..1f,
                valueLabel = { it.toString().take(4) },
                summary = { "阻尼比值越大，滚动衰减越快" },
                onValueChangeFinished = { persist() }
            )
            slider(
                key = "lyric_scroll_spring_stiffness",
                title = { "歌词滚动刚度（默认100）" },
                value = settings.value.scrollSpringStiffness,
                onValueChange = { updateInMemory { copy(scrollSpringStiffness = it) } },
                valueRange = 1f..400f,
                valueLabel = { it.toInt().toString() },
                summary = { "刚度数值越大，滚动速度越快" },
                onValueChangeFinished = { persist() }
            )

            switch(
                key = "lyric_only_current_translation",
                title = { "歌词翻译只显示当前行" },
                summary = { "随君喜好开启" },
                value = settings.value.onlyCurrentTranslationVisible,
                onValueChange = { updateAndPersist { copy(onlyCurrentTranslationVisible = it) } }
            )
            switch(
                key = "lyric_blur_effect",
                title = { "歌词模糊效果" },
                summary = { "为歌词添加一点模糊效果" },
                value = settings.value.blurEffectEnable,
                onValueChange = { updateAndPersist { copy(blurEffectEnable = it) } }
            )
            switch(
                key = "lyric_translation_visible",
                title = { "显示翻译" },
                value = settings.value.translationVisible,
                onValueChange = { updateAndPersist { copy(translationVisible = it) } }
            )

            if (includeFontEntry) {
                click(
                    key = "lyric_font",
                    title = { "歌词字体" },
                    summary = {
                        val state by fontManager.state.collectAsState()
                        val name = state.fonts.firstOrNull { it.appliedLyric }?.name
                        if (name != null) {
                            "当前: $name（在字体管理中更换）"
                        } else {
                            "在字体管理中配置"
                        }
                    },
                    onClick = {
                        AppRouter.route("/settings/fonts").jump()
                    }
                )
            }
        }
    }
}

/**
 * 主设置页的歌词入口组：只暴露一个 click 入口，点击后跳转到歌词设置子页
 * `/settings/lyric`。17 项完整设置由子页承载，不再堆积到主设置页。
 */
@Factory
@Named("settings_lyric")
fun provideLyricSettingsEntry(): SettingsGroup = settingsGroup(
    key = "lyric",
    order = 12,
    title = { "歌词" },
    description = { "歌词显示样式" },
) {
    click(
        key = "lyric.settings",
        title = { "歌词设置" },
        summary = { "字号、对齐、翻译、滚动效果等完整设置" },
        onClick = {
            AppRouter.route("/settings/lyric").jump()
        }
    )
}

/**
 * 播放页弹窗用的精简歌词设置组：只保留高频调整项（对齐 / 主歌词字号 /
 * 翻译字号 / 翻译开关），外加「完整歌词设置」入口跳转子页。
 *
 * 与 [provideLyricSettings] 共享同一份 `LyricSettings` KV（实时预览 + 松手落盘）。
 */
fun provideLyricSettingsQuick(
    fontManager: FontManager,
): SettingsGroup {
    val settings = KVContext.obtainStatic<LyricSettings>(
        key = "LyricSettings",
        defaultValue = LyricSettings(),
    ).apply { disableAutoSave() }

    /** 仅更新内存 state（驱动歌词渲染实时反馈），不落盘。 */
    fun updateInMemory(block: LyricSettings.() -> LyricSettings) {
        settings.value = settings.value.block()
    }

    /** 强制落盘。 */
    fun persist() = settings.save()

    /** 内存 + 落盘一步完成（dropdown / switch 等离散选择场景）。 */
    fun updateAndPersist(block: LyricSettings.() -> LyricSettings) {
        updateInMemory(block)
        persist()
    }

    return settingsGroup(
        key = "lyric_quick",
        order = 0,
        title = { "歌词" },
        description = { "常用歌词设置" },
    ) {
        dropdown(
            key = "lyric_quick_text_align",
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
            onValueChange = { updateAndPersist { copy(textAlign = it) } },
            serialize = { it.toString() },
            deserialize = { name ->
                when (name) {
                    "Center" -> TextAlign.Center
                    "End" -> TextAlign.End
                    else -> TextAlign.Start
                }
            },
            optionIcon = { align ->
                when (align) {
                    TextAlign.Start -> RemixIcon.Editor.alignLeft
                    TextAlign.Center -> RemixIcon.Editor.alignCenter
                    else -> RemixIcon.Editor.alignRight
                }
            }
        )
        slider(
            key = "lyric_quick_main_font_size",
            title = { "歌词文字大小" },
            value = settings.value.mainFontSize.value,
            onValueChange = { updateInMemory { copy(mainFontSize = it.sp) } },
            valueRange = 14f..64f,
            valueLabel = { "${it.toInt()} sp" },
            onValueChangeFinished = { persist() }
        )
        slider(
            key = "lyric_quick_translation_font_size",
            title = { "翻译文字大小" },
            value = settings.value.translationFontSize.value,
            onValueChange = { updateInMemory { copy(translationFontSize = it.sp) } },
            valueRange = 14f..64f,
            valueLabel = { "${it.toInt()} sp" },
            onValueChangeFinished = { persist() }
        )
        switch(
            key = "lyric_quick_translation_visible",
            title = { "显示翻译" },
            value = settings.value.translationVisible,
            onValueChange = { updateAndPersist { copy(translationVisible = it) } }
        )
        click(
            key = "lyric_quick_full_settings",
            title = { "完整歌词设置" },
            summary = { "行高、字重、偏移、滚动效果等" },
            onClick = {
                AppRouter.route("/settings/lyric").jump()
            }
        )
    }
}
