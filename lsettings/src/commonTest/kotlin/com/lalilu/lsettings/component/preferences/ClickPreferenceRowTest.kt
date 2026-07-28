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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.lalilu.common.settings.ClickPreference
import kotlin.test.Test
import kotlin.test.assertEquals


@OptIn(ExperimentalTestApi::class)
class ClickPreferenceRowTest {

    @Test
    fun `clicking the row invokes onClick`() = runComposeUiTest {
        var invocations = 0
        setContent {
            ClickPreferenceRow(
                pref = ClickPreference(
                    key = "btn",
                    title = { "Press" },
                    onClick = { invocations++ }
                )
            )
        }
        onNodeWithTag("preference_click_btn").assertIsDisplayed()
        onNodeWithTag("preference_click_btn").performClick()
        assertEquals(1, invocations)
        onNodeWithTag("preference_click_btn").performClick()
        assertEquals(2, invocations)
    }

    @Test
    fun `disabled row does not respond to clicks`() = runComposeUiTest {
        var invocations = 0
        setContent {
            ClickPreferenceRow(
                pref = ClickPreference(
                    key = "btn",
                    title = { "Press" },
                    onClick = { invocations++ },
                    enabled = { false }
                )
            )
        }
        onNodeWithTag("preference_click_btn").assertIsDisplayed()
        onNodeWithTag("preference_click_btn").performClick()
        assertEquals(0, invocations)
    }
}
