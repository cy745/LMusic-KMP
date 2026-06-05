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
import kotlin.test.assertTrue


class SettingsGroupTest {

    @Test
    fun `preferences lambda is lazy`() {
        var invoked = 0
        val group = SettingsGroup(
            key = "g",
            preferences = {
                invoked++
                emptyList<Preference<*>>()
            }
        )
        // Construction must not trigger the lambda.
        assertEquals(0, invoked)
        // First call invokes once.
        group.preferences()
        assertEquals(1, invoked)
        // Subsequent calls re-invoke (the lambda is not cached by design).
        group.preferences()
        assertEquals(2, invoked)
    }

    @Test
    fun `order default is zero`() {
        val group = SettingsGroup(key = "g", preferences = { emptyList() })
        assertEquals(0, group.order)
    }

    @Test
    fun `key is preserved as-is`() {
        val group = SettingsGroup(key = "lplayer", preferences = { emptyList() })
        assertEquals("lplayer", group.key)
    }

    @Test
    fun `groups are sortable by order then key`() {
        val groups = listOf(
            SettingsGroup("b", order = 20, preferences = { emptyList() }),
            SettingsGroup("a", order = 10, preferences = { emptyList() }),
            SettingsGroup("c", order = 10, preferences = { emptyList() }),
        )
        val sorted = groups.sortedWith(compareBy({ it.order }, { it.key }))
        assertEquals(listOf("a", "c", "b"), sorted.map { it.key })
    }

    @Test
    fun `empty preferences lambda returns empty list`() {
        val group = SettingsGroup(key = "g", preferences = { emptyList() })
        assertTrue(group.preferences().isEmpty())
    }
}
