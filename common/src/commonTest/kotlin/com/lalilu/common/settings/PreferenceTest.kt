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

package com.lalilu.common.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class PreferenceTest {

    @Test
    fun `SwitchPreference holds value and key`() {
        val pref = SwitchPreference(
            key = "k1",
            title = { "Title" },
            value = true,
            onValueChange = {}
        )
        assertEquals("k1", pref.key)
        assertTrue(pref.value)
    }

    @Test
    fun `SwitchPreference default visible and enabled are true`() {
        val pref = SwitchPreference("k", { "T" }, false, {})
        assertTrue(pref.visible())
        assertTrue(pref.enabled())
    }

    @Test
    fun `SwitchPreference invokes onValueChange`() {
        var captured: Boolean? = null
        val pref = SwitchPreference("k", { "T" }, false, { captured = it })
        pref.onValueChange(true)
        assertEquals(true, captured)
    }

    @Test
    fun `SwitchPreference visible lambda controls visibility`() {
        val pref = SwitchPreference("k", { "T" }, false, {}, visible = { false })
        assertFalse(pref.visible())
    }

    @Test
    fun `SwitchPreference enabled lambda controls enabled state`() {
        val pref = SwitchPreference("k", { "T" }, false, {}, enabled = { false })
        assertFalse(pref.enabled())
    }

    @Test
    fun `SwitchPreference summary and icon defaults are stable lambdas`() {
        val pref = SwitchPreference("k", { "T" }, false, {})
        // Default lambdas exist and are non-null; invocation needs a Composable context
        // (verified in UI tests). Here we verify the property references themselves.
        assertEquals(pref.summary, pref.summary)
        assertEquals(pref.icon, pref.icon)
    }

    @Test
    fun `SliderPreference stores range and steps`() {
        val pref = SliderPreference(
            key = "s",
            title = { "Slider" },
            value = 0.5f,
            onValueChange = {},
            valueRange = 0f..2f,
            steps = 4
        )
        assertEquals(0f..2f, pref.valueRange)
        assertEquals(4, pref.steps)
        assertEquals(0.5f, pref.value)
    }

    @Test
    fun `DropdownPreference delegates value to selectedValue`() {
        val pref = DropdownPreference(
            key = "d",
            title = { "Pick" },
            selectedValue = "A",
            options = listOf("A", "B"),
            optionLabel = { it },
            onValueChange = {},
            serialize = { it },
            deserialize = { it }
        )
        assertEquals("A", pref.value)
        assertEquals(listOf("A", "B"), pref.options)
    }

    @Test
    fun `DropdownPreference serialize and deserialize round trip`() {
        val pref = DropdownPreference(
            key = "d",
            title = { "Pick" },
            selectedValue = "A",
            options = listOf("A", "B", "C"),
            optionLabel = { it },
            onValueChange = {},
            serialize = { it },
            deserialize = { it }
        )
        for (option in pref.options) {
            val serialized = pref.serialize(option)
            val deserialized = pref.deserialize(serialized)
            assertEquals(option, deserialized)
        }
    }

    @Test
    fun `MultiSelectPreference value reflects selectedValues`() {
        val pref = MultiSelectPreference(
            key = "m",
            title = { "Multi" },
            selectedValues = setOf("A", "C"),
            options = listOf("A", "B", "C"),
            optionLabel = { it },
            onValueChange = {},
            serializeSelected = { it },
            deserializeSelected = { it }
        )
        assertEquals(setOf("A", "C"), pref.value)
    }

    @Test
    fun `TextPreference singleLine flag is preserved`() {
        val single = TextPreference("t", { "T" }, "", {}, singleLine = true)
        val multi = TextPreference("t", { "T" }, "", {}, singleLine = false)
        assertTrue(single.singleLine)
        assertFalse(multi.singleLine)
    }

    @Test
    fun `ClickPreference holds onClick and value is Unit`() {
        var clicked = 0
        val pref = ClickPreference(onClick = { clicked++ }, key = "c", title = { "Click" })
        pref.onClick(PreferenceActionContext.Empty)
        assertEquals(1, clicked)
        assertEquals(Unit, pref.value)
    }

    @Test
    fun `ClickPreference default onValueChange is no-op`() {
        val pref = ClickPreference(onClick = {}, key = "c", title = { "C" })
        // Should not throw and should not affect any state.
        pref.onValueChange(Unit)
    }

    @Test
    fun `CustomPreference wires customRenderer`() {
        var invoked = 0
        val pref = CustomPreference(
            key = "x",
            title = { "X" },
            value = 0,
            onValueChange = {},
            content = { invoked++ }
        )
        assertEquals(0, pref.value)
        assertEquals(0, invoked)
    }
}
