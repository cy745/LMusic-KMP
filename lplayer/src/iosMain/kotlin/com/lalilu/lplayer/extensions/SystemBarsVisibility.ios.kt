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
 * iOS 上隐藏状态栏需要宿主 App 在根 ViewController 覆写 `prefersStatusBarHidden`
 * 再调用 `setNeedsStatusBarAppearanceUpdate()`，库内无法可靠接管，故此处为空操作。
 * 若后续要支持，应在 composeApp 的 iOS 入口创建对应的 ViewController 子类，
 * 并把可见性状态通过依赖注入传给播放页。
 */
@Composable
actual fun SystemBarsVisibilityEffect(visible: Boolean) = Unit
