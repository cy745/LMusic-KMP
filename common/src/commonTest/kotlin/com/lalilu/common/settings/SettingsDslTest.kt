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

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.common.kv.testing.TestKVContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue


class SettingsDslTest {

    private class TestPlayerKV(saver: KVSaver) : TestKVContext("test_player", saver) {
        val autoPlay = obtain<Boolean>("auto_play", false)
        val playMode = obtain<String>("play_mode", "ListRecycle")
        val title = obtain<String>("title", "")
    }

    private val store: MutableMap<String, Any?> = mutableMapOf()
    private lateinit var saver: InMemoryKVSaver
    private lateinit var kv: TestPlayerKV

    @BeforeTest
    fun setup() {
        saver = InMemoryKVSaver(store)
        kv = TestPlayerKV(saver)
    }

    @AfterTest
    fun teardown() {
        // clear global kvMap so other tests are isolated
        KVContext.kvMap.clear()
    }

    @Test
    fun `switch with explicit key returns SwitchPreference`() {
        val group = settingsGroup("g") {
            switch(key = "k1", title = { "T" }, value = false, onValueChange = {})
        }
        val prefs = group.preferences()
        assertEquals(1, prefs.size)
        val p = prefs[0]
        assertTrue(p is SwitchPreference)
        assertEquals("k1", p.key)
    }

    @Test
    fun `switch with kv item reads and writes through kv value`() {
        val group = settingsGroup("g") {
            switch(kv = kv.autoPlay, title = { "T" })
        }
        val pref = group.preferences().first() as SwitchPreference
        assertEquals(false, pref.value)
        pref.onValueChange(true)
        assertEquals(true, kv.autoPlay.value)
        assertEquals(true, store[kv.autoPlay.key])
    }

    @Test
    fun `slider with explicit key and range stores fields`() {
        val group = settingsGroup("g") {
            slider(
                key = "s",
                title = { "Slider" },
                value = 0.25f,
                onValueChange = {},
                valueRange = 0f..1f,
                steps = 8
            )
        }
        val pref = group.preferences().first() as SliderPreference
        assertEquals(0f..1f, pref.valueRange)
        assertEquals(8, pref.steps)
        assertEquals(0.25f, pref.value)
    }

    @Test
    fun `dropdown round-trips selected value through kv string`() {
        val group = settingsGroup("g") {
            dropdown(
                kv = kv.playMode,
                title = { "Mode" },
                options = listOf("ListRecycle", "RepeatOne", "Shuffle"),
                optionLabel = { it },
                serialize = { it },
                deserialize = { it },
                fallback = "ListRecycle"
            )
        }
        val pref = group.preferences().first() as DropdownPreference<String>
        assertEquals("ListRecycle", pref.value)

        pref.onValueChange("Shuffle")
        assertEquals("Shuffle", kv.playMode.value)
        assertEquals("Shuffle", store[kv.playMode.key])
    }

    @Test
    fun `text stores value and onValueChange writes through kv`() {
        val group = settingsGroup("g") {
            text(kv.title, title = { "Title" })
        }
        val pref = group.preferences().first() as TextPreference
        assertEquals("", pref.value)
        pref.onValueChange("hello")
        assertEquals("hello", kv.title.value)
    }

    @Test
    fun `click preference invokes callback with context`() {
        var capturedCtx: PreferenceActionContext? = null
        val group = settingsGroup("g") {
            click(
                key = "btn",
                title = { "Go" },
                onClick = { ctx -> capturedCtx = ctx }
            )
        }
        val pref = group.preferences().first() as ClickPreference
        pref.onClick(PreferenceActionContext.Empty)
        assertSame(PreferenceActionContext.Empty, capturedCtx)
    }

    @Test
    fun `DSL supports multiple preferences in one group`() {
        val group = settingsGroup("g") {
            switch("a", { "A" }, false, {})
            switch("b", { "B" }, true, {})
            click("c", { "C" }, {})
        }
        assertEquals(3, group.preferences().size)
        assertEquals(listOf("a", "b", "c"), group.preferences().map { it.key })
    }

    @Test
    fun `settingsGroup key and order are preserved`() {
        val group = settingsGroup(key = "lplayer", order = 42) {
            switch("a", { "A" }, false, {})
        }
        assertEquals("lplayer", group.key)
        assertEquals(42, group.order)
    }

    @Test
    fun `multiSelect with explicit set returns MultiSelectPreference`() {
        val group = settingsGroup("g") {
            multiSelect<String>(
                key = "m",
                title = { "M" },
                selectedValues = setOf("A"),
                options = listOf("A", "B"),
                optionLabel = { it },
                onValueChange = {},
                serializeSelected = { it },
                deserializeSelected = { it }
            )
        }
        val pref = group.preferences().first() as MultiSelectPreference<String>
        assertEquals(setOf("A"), pref.value)
    }
}
