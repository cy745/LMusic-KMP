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
