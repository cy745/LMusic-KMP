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

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class SettingsCollectorTest {

    private fun mkGroup(key: String, order: Int): SettingsGroup =
        SettingsGroup(
            key = key,
            order = order,
            preferences = { emptyList() }
        )

    @BeforeTest
    fun startKoinContext() {
        // Each test starts a fresh global Koin. stopKoin in @AfterTest ensures isolation.
    }

    @AfterTest
    fun stopKoinContext() {
        try {
            KoinPlatform.getKoin()
            stopKoin()
        } catch (_: Exception) {
            // Koin not started, nothing to stop
        }
    }

    private inline fun <T> withKoin(
        vararg modules: org.koin.core.module.Module,
        block: () -> T
    ): T {
        startKoin { modules(*modules) }
        return block()
    }

    @Test
    fun `collectAll returns empty list when no groups are registered`() {
        withKoin {
            assertTrue(SettingsCollector.collectAll().isEmpty())
        }
    }

    @Test
    fun `collectAll returns groups sorted by order then key`() {
        withKoin(
            module {
                factory<SettingsGroup>(named("b")) { mkGroup("b", order = 20) }
                factory<SettingsGroup>(named("a")) { mkGroup("a", order = 10) }
                factory<SettingsGroup>(named("c")) { mkGroup("c", order = 10) }
            }
        ) {
            val groups = SettingsCollector.collectAll()
            assertEquals(listOf("a", "c", "b"), groups.map { it.key })
        }
    }

    @Test
    fun `getByKey returns matching group or null`() {
        withKoin(
            module {
                factory<SettingsGroup>(named("lplayer")) { mkGroup("lplayer", 10) }
                factory<SettingsGroup>(named("lhome")) { mkGroup("lhome", 20) }
            }
        ) {
            assertNotNull(SettingsCollector.getByKey("lplayer"))
            assertNull(SettingsCollector.getByKey("missing"))
        }
    }

    @Test
    fun `collectAll does not pick up unrelated types`() {
        withKoin(
            module {
                factory<SettingsGroup>(named("g")) { mkGroup("g", 0) }
                factory<String>(named("not-a-group")) { "string" }
            }
        ) {
            val groups = SettingsCollector.collectAll()
            assertEquals(1, groups.size)
            assertEquals("g", groups.single().key)
        }
    }

    @Test
    fun `stable sort for ties keeps same order groups in key ascending order`() {
        withKoin(
            module {
                factory<SettingsGroup>(named("z")) { mkGroup("z", order = 5) }
                factory<SettingsGroup>(named("a")) { mkGroup("a", order = 5) }
                factory<SettingsGroup>(named("m")) { mkGroup("m", order = 5) }
            }
        ) {
            val groups = SettingsCollector.collectAll()
            assertEquals(listOf("a", "m", "z"), groups.map { it.key })
        }
    }
}
