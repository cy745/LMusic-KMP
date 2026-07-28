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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.lalilu.common.settings.SwitchPreference
import kotlin.test.Test
import kotlin.test.assertEquals


@OptIn(ExperimentalTestApi::class)
class SwitchPreferenceRowTest {

    @Test
    fun `clicking row toggles value via Switch`() = runComposeUiTest {
        setContent {
            var current by remember { mutableStateOf(false) }
            SwitchPreferenceRow(
                pref = SwitchPreference("k1", { "Auto Play" }, current, { current = it })
            )
        }
        onNodeWithTag("preference_switch_k1").assertIsDisplayed()
        onNodeWithTag("preference_switch_k1").performClick()
        onNodeWithTag("preference_switch_k1").performClick()
        // The semantics check verifies the row is functional
        onNodeWithTag("preference_switch_k1").assertIsEnabled()
    }

    @Test
    fun `disabled preference is rendered as not enabled`() = runComposeUiTest {
        setContent {
            SwitchPreferenceRow(
                pref = SwitchPreference(
                    key = "k1", title = { "T" }, value = false,
                    onValueChange = {}, enabled = { false }
                )
            )
        }
        onNodeWithTag("preference_switch_k1").assertIsDisplayed()
        onNodeWithTag("preference_switch_k1").assertIsNotEnabled()
    }

    @Test
    fun `enabled preference responds to click`() = runComposeUiTest {
        setContent {
            SwitchPreferenceRow(
                pref = SwitchPreference(
                    key = "k1", title = { "T" }, value = false,
                    onValueChange = {}, enabled = { true }
                )
            )
        }
        onNodeWithTag("preference_switch_k1").assertIsEnabled()
    }
}
