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
import com.lalilu.common.settings.TextPreference
import kotlin.test.Test


@OptIn(ExperimentalTestApi::class)
class TextPreferenceRowTest {

    @Test
    fun `text row renders with correct tag`() = runComposeUiTest {
        setContent {
            TextPreferenceRow(
                pref = TextPreference(
                    key = "user_name",
                    title = { "User Name" },
                    value = "alice",
                    onValueChange = {}
                )
            )
        }
        onNodeWithTag("preference_text_user_name").assertIsDisplayed()
    }

    @Test
    fun `text row shows placeholder when value is blank`() = runComposeUiTest {
        setContent {
            TextPreferenceRow(
                pref = TextPreference(
                    key = "empty_field",
                    title = { "Field" },
                    value = "",
                    onValueChange = {}
                )
            )
        }
        onNodeWithTag("preference_text_empty_field").assertIsDisplayed()
    }
}
