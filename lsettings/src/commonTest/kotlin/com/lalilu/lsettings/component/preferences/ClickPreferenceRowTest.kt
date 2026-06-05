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
