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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.lalilu.RemixIcon
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lfont.component.FontItemCard
import com.lalilu.lfont.entity.FontItem
import com.lalilu.lfont.viewmodel.FontsVM
import com.lalilu.navigation.ScreenAction
import com.lalilu.navigation.ScreenActionFactory
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import com.lalilu.navigation.smartbar.NavigatorHeader
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * 自定义字体管理页入口。
 *
 * 路由：`/settings/fonts`（KRouter）
 */
@Destination("/settings/fonts")
data object FontsScreen : Screen, ScreenInfoFactory, ScreenActionFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "自定义字体" },
            icon = RemixIcon.Editor.text
        )
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = koinViewModel<FontsVM>()
        val scope = rememberCoroutineScope()

        return remember {
            listOf(
                ScreenAction.Static(
                    title = { "导入" },
                    icon = { RemixIcon.System.addLine },
                    color = { Color(0xFF009673) },
                    onAction = {
                        scope.launch {
                            val file = FileKit.openFilePicker(
                                type = FileKitType.File("ttf", "otf", "woff2"),
                                title = "选择字体文件"
                            ) ?: return@launch

                            try {
                                vm.importFont(file.readBytes(), file.name)
                            } catch (e: Exception) {
                                Logger.e(
                                    tag = "FontsScreen",
                                    messageString = "导入字体失败: ${file.name}",
                                    throwable = e
                                )
                            }
                        }
                    }
                )
            )
        }
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<FontsVM>()
        val fonts by vm.fonts.collectAsState()
        FontsScreenContent(fonts = fonts)
    }
}

@Composable
private fun FontsScreenContent(fonts: List<FontItem>) {
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
        item(key = "fonts_header") {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = "自定义字体",
                subTitle = "管理全局界面与歌词字体"
            )
        }

        if (fonts.isEmpty()) {
            item(key = "fonts_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp)
                ) {
                    Text(
                        text = "暂无字体，点击底部「导入」选择本地字体文件",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            items(
                items = fonts,
                key = { it.id }
            ) { item ->
                FontItemCard(item = item)
            }
        }
    }
}
