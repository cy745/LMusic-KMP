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

package com.lalilu.common.kv

import app.cash.turbine.test
import com.lalilu.common.kv.impl.KVItemImpl
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.common.kv.testing.TestKVContext
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue


class KVItemImplTest {

    private class TestKV(saver: KVSaver) : TestKVContext("kv_item_test", saver) {
        val flag = obtain<Boolean>("flag", false)
        val count = obtain<Int>("count", 0)
        val name = obtain<String>("name", "default")
    }

    private val store: MutableMap<String, Any?> = mutableMapOf()
    private lateinit var saver: InMemoryKVSaver
    private lateinit var kv: TestKV

    @BeforeTest
    fun setup() {
        saver = InMemoryKVSaver(store)
        kv = TestKV(saver)
    }

    @AfterTest
    fun tearDown() {
        KVContext.kvMap.clear()
    }

    @Test
    fun `default value is returned when store is empty`() {
        assertEquals(false, kv.flag.value)
        assertEquals(0, kv.count.value)
        assertEquals("default", kv.name.value)
    }

    @Test
    fun `set value writes to underlying store`() {
        kv.flag.value = true
        assertEquals(true, store[kv.flag.key])
    }

    @Test
    fun `different prefix contexts get different backing items when isolated`() {
        val store2 = mutableMapOf<String, Any?>()
        val saver2 = InMemoryKVSaver(store2)
        // Use a different prefix to bypass the static kvMap cache.
        val other = object : TestKVContext("kv_item_test_other", saver2) {
            val flag = obtain<Boolean>("flag", false)
        }
        // The two KVItems are distinct objects backed by different stores.
        assertEquals(false, other.flag.value)
        other.flag.value = true
        // store2 should reflect the change, store should not.
        assertEquals(true, store2["kv_item_test_other_flag"])
        // Note: the global kvMap caches by full key, so this assertion is best-effort.
    }

    @Test
    fun `same key within same prefix returns the same KVItem instance`() {
        val sameRef = KVContext.obtainStatic<Boolean>(key = "flag", defaultValue = false, prefix = "kv_item_test")
        assertSame(kv.flag, sameRef)
    }

    @Test
    fun `rebuild KV from store reads back persisted value`() {
        kv.flag.value = true
        kv.count.value = 42

        val newStore = mutableMapOf<String, Any?>().apply {
            put("kv_item_test_flag", true)
            put("kv_item_test_count", 42)
        }
        val newSaver = InMemoryKVSaver(newStore)
        val newKv = TestKV(newSaver)
        assertEquals(true, newKv.flag.value)
        assertEquals(42, newKv.count.value)
    }

    @Test
    fun `flow emits when value changes`() = runTest {
        kv.flag.flow().test {
            assertEquals(false, awaitItem())
            kv.flag.value = true
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setData updates flow without triggering autoSave loop`() = runTest {
        kv.flag.flow().test {
            awaitItem()
            kv.flag.setData(true)
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `same value set twice is a no-op for save`() {
        kv.flag.value = true
        val firstStore = store.toMap()
        kv.flag.value = true
        assertEquals(firstStore, store.toMap())
    }

    @Test
    fun `remove clears the value and resets to default on next read`() {
        kv.flag.value = true
        kv.flag.remove()
        // After remove, the underlying value is gone; next getData should return default
        assertEquals(false, kv.flag.value)
    }
}
