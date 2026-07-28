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
