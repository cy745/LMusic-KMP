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

package com.lalilu.lsettings.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test


@OptIn(ExperimentalTestApi::class)
class TextInputDialogTest {

    @Test
    fun `text input dialog renders field and confirm button`() = runComposeUiTest {
        setContent {
            TextInputDialog(
                title = "Edit",
                initial = "initial value",
                singleLine = true,
                hint = "type here",
                onDismiss = {},
                onConfirm = {}
            )
        }
        onNodeWithTag("text_input_dialog_field").assertIsDisplayed()
        onNodeWithTag("text_input_dialog_confirm").assertIsDisplayed()
    }

    @Test
    fun `text input dialog supports multi-line mode`() = runComposeUiTest {
        setContent {
            TextInputDialog(
                title = "Bio",
                initial = "Line 1\nLine 2",
                singleLine = false,
                onDismiss = {},
                onConfirm = {}
            )
        }
        onNodeWithTag("text_input_dialog_field").assertIsDisplayed()
    }
}
