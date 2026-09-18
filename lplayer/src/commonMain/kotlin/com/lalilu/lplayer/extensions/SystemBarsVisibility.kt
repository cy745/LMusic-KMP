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

package com.lalilu.lplayer.extensions

import androidx.compose.runtime.Composable

/**
 * 让宿主窗口的系统状态栏随 [visible] 显示 / 隐藏。
 *
 * 移植自单端 LMusic 播放页里的 `systemUiController.isStatusBarVisible = !hideComponent`：
 * 歌词页展开且开启"隐藏其他组件"时，系统状态栏也要一起隐藏。
 *
 * 各平台实现：
 * - Android：`WindowInsetsControllerCompat` 控制状态栏（含 API 30 以下的兼容路径）
 * - JVM 桌面 / Web：无系统状态栏，空操作
 * - iOS：空操作。隐藏状态栏需要宿主 App 在根 ViewController 上覆写
 *   `prefersStatusBarHidden`，属于 App 层职责，库内无法可靠接管
 *
 * 离开组合时会恢复显示，避免影响其它页面。
 */
@Composable
expect fun SystemBarsVisibilityEffect(visible: Boolean)
