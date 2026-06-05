/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
