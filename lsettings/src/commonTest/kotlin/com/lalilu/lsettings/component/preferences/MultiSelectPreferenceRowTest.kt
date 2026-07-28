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
import com.lalilu.common.settings.MultiSelectPreference
import kotlin.test.Test


@OptIn(ExperimentalTestApi::class)
class MultiSelectPreferenceRowTest {

    @Test
    fun `multi select row renders with correct tag`() = runComposeUiTest {
        setContent {
            MultiSelectPreferenceRow(
                pref = MultiSelectPreference(
                    key = "tags",
                    title = { "Tags" },
                    selectedValues = setOf("A"),
                    options = listOf("A", "B", "C"),
                    optionLabel = { it },
                    onValueChange = {},
                    serializeSelected = { it },
                    deserializeSelected = { it }
                )
            )
        }
        onNodeWithTag("preference_multiselect_tags").assertIsDisplayed()
    }
}
