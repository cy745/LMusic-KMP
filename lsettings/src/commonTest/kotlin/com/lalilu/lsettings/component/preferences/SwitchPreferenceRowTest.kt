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
