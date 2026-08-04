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

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** Web 暂用默认字体；NotoSansSC-Regular 在 Web 阶段接入。 */
@Composable
actual fun platformDefaultFontFamily(): FontFamily = FontFamily.Default
