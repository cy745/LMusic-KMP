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

package com.lalilu.lfont.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.RemixIcon
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lfont.component.fontMarquee
import com.lalilu.lfont.component.label
import com.lalilu.lfont.entity.FontItem
import com.lalilu.lfont.manager.FontManager
import com.lalilu.lfont.preview.rememberPreviewFont
import com.lalilu.lfont.util.formatFileSize
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.NavigatorHeader
import org.koin.compose.koinInject

/** 长文本跑马灯预览：覆盖中文、英文、数字、日文。 */
private const val SAMPLE_MARQUEE =
    "字体预览 Font Preview 0123 中文 English 日本語 ひらがな カタカナ 漢字 あいうえお アイウエオ "

private const val SAMPLE_TITLE = "字体预览 Aa 0123"
private const val SAMPLE_CHINESE = "中文预览：春眠不觉晓，处处闻啼鸟。夜来风雨声，花落知多少。"
private const val SAMPLE_LATIN = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789"
private const val SAMPLE_JAPANESE =
    "日本語のサンプルです。ひらがな：あいうえお かきくけこ。カタカナ：アイウエオ カキクケコ。漢字：東京 日本語 漢字テスト。"
private const val SAMPLE_PUNCT = "标点符号：！？。、，；：（）「」『』【】—…·《》"

/**
 * 字体详细预览页。
 *
 * 路由：`/settings/fonts/detail`（参数 fileName）
 */
@Destination("/settings/fonts/detail")
data class FontDetailScreen(
    val fileName: String,
) : Screen, ScreenInfoFactory {

    override val key: String = "${super.key}:$fileName"

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "字体详情" },
            icon = RemixIcon.Editor.text
        )
    }

    @Composable
    override fun Content() {
        val fontManager = koinInject<FontManager>()
        val state by fontManager.state.collectAsState()
        FontDetailScreenContent(
            item = state.fonts.firstOrNull { it.id == fileName },
            fileName = fileName
        )
    }
}

@Composable
private fun FontDetailScreenContent(
    item: FontItem?,
    fileName: String,
) {
    val previewFont = rememberPreviewFont(fileName)
    val background = MaterialTheme.colorScheme.background

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBar.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp
        )
    ) {
        item(key = "detail_header") {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = item?.name ?: fileName,
                subTitle = "字体详细预览"
            )
        }

        item(key = "detail_title") {
            SectionTitle("大号预览")
            Text(
                text = SAMPLE_TITLE,
                modifier = Modifier.padding(horizontal = 16.dp),
                fontFamily = previewFont ?: FontFamily.Default,
                fontSize = 40.sp,
                lineHeight = 48.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item(key = "detail_marquee") {
            SectionTitle("长文本跑马灯")
            Text(
                text = SAMPLE_MARQUEE,
                modifier = Modifier.fontMarquee(background),
                fontFamily = previewFont ?: FontFamily.Default,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item(key = "detail_chinese") {
            SectionTitle("中文")
            SampleText(text = SAMPLE_CHINESE, fontFamily = previewFont)
        }

        item(key = "detail_latin") {
            SectionTitle("英文与数字")
            SampleText(text = SAMPLE_LATIN, fontFamily = previewFont)
        }

        item(key = "detail_japanese") {
            SectionTitle("日文")
            SampleText(text = SAMPLE_JAPANESE, fontFamily = previewFont)
        }

        item(key = "detail_punct") {
            SectionTitle("标点符号")
            SampleText(text = SAMPLE_PUNCT, fontFamily = previewFont)
        }

        item(key = "detail_info") {
            SectionTitle("字体信息")
            InfoRow(label = "名称", value = item?.name ?: fileName)
            InfoRow(label = "文件", value = item?.fileName ?: fileName)
            InfoRow(label = "来源", value = item?.source?.label() ?: "导入")
            InfoRow(label = "大小", value = item?.let { formatFileSize(it.fileSize) } ?: "未知")
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    )
}

@Composable
private fun SampleText(
    text: String,
    fontFamily: FontFamily?,
) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp),
        fontFamily = fontFamily ?: FontFamily.Default,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(end = 16.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}
