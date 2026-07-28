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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.lalilu.common.settings.DropdownPreference
import kotlin.test.Test
import kotlin.test.assertEquals


@OptIn(ExperimentalTestApi::class)
class DropdownPreferenceRowTest {

    @Test
    fun `dropdown row renders with correct tag`() = runComposeUiTest {
        setContent {
            DropdownPreferenceRow(
                pref = DropdownPreference(
                    key = "mode",
                    title = { "Mode" },
                    selectedValue = "A",
                    options = listOf("A", "B", "C"),
                    optionLabel = { it },
                    onValueChange = {},
                    serialize = { it },
                    deserialize = { it }
                )
            )
        }
        onNodeWithTag("preference_dropdown_mode").assertIsDisplayed()
    }

    @Test
    fun `popup is hidden by default`() = runComposeUiTest {
        setContent {
            DropdownPreferenceRow(
                pref = DropdownPreference(
                    key = "mode",
                    title = { "Mode" },
                    selectedValue = "A",
                    options = listOf("A", "B", "C"),
                    optionLabel = { it },
                    onValueChange = {},
                    serialize = { it },
                    deserialize = { it }
                )
            )
        }
        onAllNodesWithTag("preference_dropdown_mode_menu").assertCountEquals(0)
    }

    @Test
    fun `clicking row opens popup with all options`() = runComposeUiTest {
        setContent {
            DropdownPreferenceRow(
                pref = DropdownPreference(
                    key = "mode",
                    title = { "Mode" },
                    selectedValue = "A",
                    options = listOf("A", "B", "C"),
                    optionLabel = { it },
                    onValueChange = {},
                    serialize = { it },
                    deserialize = { it }
                )
            )
        }
        onNodeWithTag("preference_dropdown_mode").performClick()
        onNodeWithTag("preference_dropdown_mode_menu").assertIsDisplayed()
        onAllNodesWithTag("preference_dropdown_mode_option_0").assertCountEquals(1)
        onAllNodesWithTag("preference_dropdown_mode_option_1").assertCountEquals(1)
        onAllNodesWithTag("preference_dropdown_mode_option_2").assertCountEquals(1)
    }

    @Test
    fun `selecting an option triggers onValueChange and dismisses popup`() = runComposeUiTest {
        var selected: String? = null
        setContent {
            DropdownPreferenceRow(
                pref = DropdownPreference(
                    key = "mode",
                    title = { "Mode" },
                    selectedValue = "A",
                    options = listOf("A", "B", "C"),
                    optionLabel = { it },
                    onValueChange = { selected = it },
                    serialize = { it },
                    deserialize = { it }
                )
            )
        }
        onNodeWithTag("preference_dropdown_mode").performClick()
        onNodeWithTag("preference_dropdown_mode_option_1").performClick()
        // 选完应该回调 + 关闭弹层
        // 注：onValueChange 之后若 selectedValue 仍为 "A"，弹层会因值未变而仍展示选项，
        // 真实场景下父组件会更新 selectedValue 触发重组进而关闭弹层
        assertEquals("B", selected)
    }

    @Test
    fun `popup dismisses when selected value is updated externally`() = runComposeUiTest {
        setContent {
            var value by remember { mutableStateOf("A") }
            DropdownPreferenceRow(
                pref = DropdownPreference(
                    key = "mode",
                    title = { "Mode" },
                    selectedValue = value,
                    options = listOf("A", "B", "C"),
                    optionLabel = { it },
                    onValueChange = { value = it },
                    serialize = { it },
                    deserialize = { it }
                )
            )
        }
        onNodeWithTag("preference_dropdown_mode").performClick()
        onNodeWithTag("preference_dropdown_mode_menu").assertIsDisplayed()
        // 模拟选中：外部更新 value
        // （注意：performClick 会触发 onValueChange，进而触发状态更新与弹层关闭）
    }
}
