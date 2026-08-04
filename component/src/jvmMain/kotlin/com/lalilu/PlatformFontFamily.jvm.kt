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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lalilu.component.component.generated.resources.Res
import com.lalilu.component.component.generated.resources.noto_sans_sc_vf
import org.jetbrains.compose.resources.Font

/** JVM 使用附带可变字体（jvmMain 资源，仅桌面端打包）。 */
@Composable
actual fun platformDefaultFontFamily(): FontFamily {
    val fontWeight = remember { (100..900 step 100).map { FontWeight(it) } }
    val fonts = fontWeight.map { Font(resource = Res.font.noto_sans_sc_vf, weight = it) }
    return remember { FontFamily(fonts) }
}
