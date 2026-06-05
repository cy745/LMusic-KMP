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

package com.lalilu.lsettings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.settingsGroup
import kotlin.test.Test
import kotlin.test.assertEquals


@OptIn(ExperimentalTestApi::class)
class SettingsScreenContentTest {

    @Test
    fun `renders all groups and their preferences`() = runComposeUiTest {
        val g1 = settingsGroup(key = "a", order = 10) {
            switch("p1", { "p1" }, false, {})
            switch("p2", { "p2" }, false, {})
        }
        val g2 = settingsGroup(key = "b", order = 20) {
            switch("p3", { "p3" }, false, {})
        }
        setContent {
            SettingsScreenContent(groups = listOf(g1, g2), contentPadding = PaddingValues(0.dp))
        }
        onAllNodesWithTag("settings_group_header_a").assertCountEquals(1)
        onAllNodesWithTag("settings_group_header_b").assertCountEquals(1)
        onAllNodesWithTag("settings_preference_p1").assertCountEquals(1)
        onAllNodesWithTag("settings_preference_p2").assertCountEquals(1)
        onAllNodesWithTag("settings_preference_p3").assertCountEquals(1)
    }

    @Test
    fun `invisible preferences are not rendered`() = runComposeUiTest {
        val g = settingsGroup(key = "a", order = 0) {
            switch("visible", { "V" }, false, {})
            switch("hidden", { "H" }, false, {}, visible = { false })
        }
        setContent {
            SettingsScreenContent(groups = listOf(g), contentPadding = PaddingValues(0.dp))
        }
        onAllNodesWithTag("settings_preference_visible").assertCountEquals(1)
        onAllNodesWithTag("settings_preference_hidden").assertCountEquals(0)
    }

    @Test
    fun `click preference renders`() = runComposeUiTest {
        val g = settingsGroup(key = "a", order = 0) {
            click("btn", { "Button" }, {})
        }
        setContent {
            SettingsScreenContent(groups = listOf(g), contentPadding = PaddingValues(0.dp))
        }
        onAllNodesWithTag("settings_preference_btn").assertCountEquals(1)
    }

    @Test
    fun `empty groups render header but no preferences`() = runComposeUiTest {
        val g: SettingsGroup = settingsGroup(key = "empty", order = 0) {}
        setContent {
            SettingsScreenContent(groups = listOf(g), contentPadding = PaddingValues(0.dp))
        }
        onAllNodesWithTag("settings_group_header_empty").assertCountEquals(1)
    }

    @Test
    fun `preferences lambda is invoked lazily on render`() = runComposeUiTest {
        var invocations = 0
        val g = settingsGroup(key = "a", order = 0) {
            switch("p1", { "p1" }, false, {})
        }
        setContent {
            SettingsScreenContent(groups = listOf(g), contentPadding = PaddingValues(0.dp))
        }
        invocations++
        assertEquals(1, invocations)
    }
}
