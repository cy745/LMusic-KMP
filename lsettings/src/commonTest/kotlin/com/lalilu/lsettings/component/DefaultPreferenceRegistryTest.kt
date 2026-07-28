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

package com.lalilu.lsettings.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.lalilu.common.settings.ClickPreference
import com.lalilu.common.settings.CustomPreference
import com.lalilu.common.settings.DropdownPreference
import com.lalilu.common.settings.MultiSelectPreference
import com.lalilu.common.settings.SliderPreference
import com.lalilu.common.settings.SwitchPreference
import com.lalilu.common.settings.TextPreference
import com.lalilu.lsettings.testutil.FakePreferenceRenderers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@OptIn(ExperimentalTestApi::class)
class DefaultPreferenceRegistryTest {

    private fun newFake() = FakePreferenceRenderers()

    @Test
    fun `SwitchPreference dispatches to renderSwitch`() = runComposeUiTest {
        val fake = newFake()
        setContent {
            CompositionLocalProvider(LocalPreferenceRenderers provides fake) {
                val renderers = LocalPreferenceRenderers.current
                renderers.render(SwitchPreference("k", { "T" }, false, {}))
            }
        }
        assertEquals(1, fake.seenTypes.size)
        assertEquals(SwitchPreference::class.java, fake.seenTypes.first())
    }

    @Test
    fun `SliderPreference dispatches to renderSlider`() = runComposeUiTest {
        val fake = newFake()
        setContent {
            CompositionLocalProvider(LocalPreferenceRenderers provides fake) {
                val renderers = LocalPreferenceRenderers.current
                renderers.render(SliderPreference("k", { "T" }, 0.5f, {}, 0f..1f, 0))
            }
        }
        assertEquals(SliderPreference::class.java, fake.seenTypes.first())
    }

    @Test
    fun `DropdownPreference dispatches to renderDropdown`() = runComposeUiTest {
        val fake = newFake()
        setContent {
            CompositionLocalProvider(LocalPreferenceRenderers provides fake) {
                val renderers = LocalPreferenceRenderers.current
                renderers.render(
                    DropdownPreference(
                        "k", { "T" }, "A", listOf("A", "B"),
                        { it }, {}, { it }, { it }
                    )
                )
            }
        }
        assertEquals(DropdownPreference::class.java, fake.seenTypes.first())
    }

    @Test
    fun `MultiSelectPreference dispatches to renderMultiSelect`() = runComposeUiTest {
        val fake = newFake()
        setContent {
            CompositionLocalProvider(LocalPreferenceRenderers provides fake) {
                val renderers = LocalPreferenceRenderers.current
                renderers.render(
                    MultiSelectPreference(
                        "k", { "T" }, emptySet(), listOf("A"),
                        { it }, {}, { it }, { it }
                    )
                )
            }
        }
        assertEquals(MultiSelectPreference::class.java, fake.seenTypes.first())
    }

    @Test
    fun `TextPreference dispatches to renderText`() = runComposeUiTest {
        val fake = newFake()
        setContent {
            CompositionLocalProvider(LocalPreferenceRenderers provides fake) {
                val renderers = LocalPreferenceRenderers.current
                renderers.render(TextPreference("k", { "T" }, "", {}))
            }
        }
        assertEquals(TextPreference::class.java, fake.seenTypes.first())
    }

    @Test
    fun `ClickPreference dispatches to renderClick`() = runComposeUiTest {
        val fake = newFake()
        setContent {
            CompositionLocalProvider(LocalPreferenceRenderers provides fake) {
                val renderers = LocalPreferenceRenderers.current
                renderers.render(ClickPreference(onClick = {}, key = "k", title = { "T" }))
            }
        }
        assertEquals(ClickPreference::class.java, fake.seenTypes.first())
    }

    @Test
    fun `CustomPreference bypasses default dispatch`() = runComposeUiTest {
        var customCalls = 0
        val customPref = CustomPreference(
            key = "k",
            title = { "T" },
            value = 0,
            onValueChange = {},
            content = { customCalls++ }
        )
        val fake = newFake()
        setContent {
            CompositionLocalProvider(LocalPreferenceRenderers provides fake) {
                val renderers = LocalPreferenceRenderers.current
                renderers.render(customPref)
            }
        }
        // The content lambda should have been invoked exactly once
        // and the default registry should NOT have dispatched.
        assertEquals(1, customCalls)
        assertTrue(fake.seenTypes.isEmpty(), "Expected no default dispatch")
    }
}
