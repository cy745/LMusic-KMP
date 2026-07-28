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
