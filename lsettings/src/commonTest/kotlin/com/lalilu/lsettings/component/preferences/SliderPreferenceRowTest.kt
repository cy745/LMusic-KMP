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

package com.lalilu.lsettings.component.preferences

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.lalilu.common.settings.SliderPreference
import kotlin.test.Test


@OptIn(ExperimentalTestApi::class)
class SliderPreferenceRowTest {

    @Test
    fun `slider row is rendered with correct tag`() = runComposeUiTest {
        setContent {
            SliderPreferenceRow(
                pref = SliderPreference(
                    key = "volume",
                    title = { "Volume" },
                    value = 0.5f,
                    onValueChange = {},
                    valueRange = 0f..1f
                )
            )
        }
        onNodeWithTag("preference_slider_volume").assertIsDisplayed()
    }

    @Test
    fun `slider row accepts custom range and steps`() = runComposeUiTest {
        setContent {
            SliderPreferenceRow(
                pref = SliderPreference(
                    key = "speed",
                    title = { "Speed" },
                    value = 1.0f,
                    onValueChange = {},
                    valueRange = 0.5f..2.0f,
                    steps = 4
                )
            )
        }
        onNodeWithTag("preference_slider_speed").assertIsDisplayed()
    }
}
