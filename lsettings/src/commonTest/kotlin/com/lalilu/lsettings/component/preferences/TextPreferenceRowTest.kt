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
