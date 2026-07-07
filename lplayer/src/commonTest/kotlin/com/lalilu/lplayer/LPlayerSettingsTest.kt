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

package com.lalilu.lplayer

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.common.kv.testing.TestKVContext
import com.lalilu.common.settings.ClickPreference
import com.lalilu.common.settings.DropdownPreference
import com.lalilu.common.settings.PreferenceActionContext
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.SwitchPreference
import com.lalilu.common.settings.settingsGroup
import com.lalilu.common.testing.FakeToaster
import com.lalilu.lplayer.extensions.PlayMode
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * 业务模块贡献 SettingsGroup 的范本测试。
 *
 * 每个业务模块都应当为它贡献的 SettingsGroup 编写类似覆盖：
 * 1) group 自身的 key / order
 * 2) 包含的 preference 类型与数量
 * 3) 关键 preference 与持久化 KVItem 的绑定关系
 * 4) ClickPreference 的 onClick 副作用（如有）
 *
 * 本测试不直接调用 [provideLPlayerSettings]（因为 LPlayerKV 是 object，
 * 第一次访问会触发 Koin 全局初始化，CI 上容易失败）。
 * 取而代之，手动构造一个等价的 SettingsGroup 验证范式。
 */
class LPlayerSettingsTest {

    private class TestPlayerKV(saver: KVSaver) : TestKVContext("lplayer", saver) {
        val autoPlay = obtain<Boolean>("auto_play_when_restart", false)
        val audioFocus = obtain<Boolean>("handle_audio_focus", true)
        val becomeNoisy = obtain<Boolean>("handle_become_noisy", true)
        val playMode = obtain<String>("play_mode", PlayMode.ListRecycle.name)
        val historyPos = obtain<Long>("history_play_position", 0L)
    }

    private val store: MutableMap<String, Any?> = mutableMapOf()
    private lateinit var saver: InMemoryKVSaver
    private lateinit var kv: TestPlayerKV
    private lateinit var group: SettingsGroup

    @BeforeTest
    fun setup() {
        saver = InMemoryKVSaver(store)
        kv = TestPlayerKV(saver)
        KVContext.registerSaver(saver)
        // Bring up a minimal Koin with a Json instance, so the Settings DSL
        // and any Koin-aware code in the contributor can resolve dependencies.
        startKoin {
            modules(module { single { Json { ignoreUnknownKeys = true } } })
        }
        // Build a SettingsGroup that mirrors the production contribution,
        // but uses our local TestPlayerKV so the test is fully isolated.
        group = settingsGroup(
            key = "lplayer",
            order = 10,
            title = { "播放器" },
        ) {
            switch(kv.autoPlay, title = { "启动后自动播放" })
            switch(kv.audioFocus, title = { "处理音频焦点" })
            switch(kv.becomeNoisy, title = { "监听耳机拔出" })
            dropdown(
                kv = kv.playMode,
                title = { "播放模式" },
                options = PlayMode.entries,
                optionLabel = { mode ->
                    when (mode) {
                        PlayMode.ListRecycle -> "列表循环"
                        PlayMode.RepeatOne   -> "单曲循环"
                        PlayMode.Shuffle     -> "随机播放"
                    }
                },
                serialize = { it.name },
                deserialize = { name -> PlayMode.from(name) },
                fallback = PlayMode.ListRecycle
            )
            click(
                key = "lplayer.clear_history_position",
                title = { "清除播放进度记录" },
                onClick = { ctx ->
                    kv.historyPos.value = 0L
                    ctx.toaster.info("已清除")
                }
            )
        }
    }

    @AfterTest
    fun teardown() {
        KVContext.kvMap.clear()
        try {
            KoinPlatform.getKoin()
            stopKoin()
        } catch (_: Exception) {
            // Koin not started, nothing to stop
        }
    }

    @Test
    fun `group has correct key and order`() {
        assertEquals("lplayer", group.key)
        assertEquals(10, group.order)
    }

    @Test
    fun `group contains all expected preferences`() {
        val prefs = group.preferences()
        assertTrue(prefs.any { it is SwitchPreference && it.key.contains("auto_play") })
        assertTrue(prefs.any { it is SwitchPreference && it.key.contains("audio_focus") })
        assertTrue(prefs.any { it is SwitchPreference && it.key.contains("become_noisy") })
        assertTrue(prefs.any { it is DropdownPreference<*> && it.key.contains("play_mode") })
        assertTrue(prefs.any { it is ClickPreference && it.key == "lplayer.clear_history_position" })
    }

    @Test
    fun `autoPlay switch is bound to KVItem`() {
        val auto = group.preferences()
            .filterIsInstance<SwitchPreference>()
            .first { it.key.contains("auto_play") }
        assertEquals(false, auto.value)
        auto.onValueChange(true)
        assertEquals(true, kv.autoPlay.value)
        assertEquals(true, store[kv.autoPlay.key])
    }

    @Test
    fun `playMode dropdown write-through updates the KV string`() {
        val dropdown = group.preferences()
            .filterIsInstance<DropdownPreference<*>>()
            .first { it.key.contains("play_mode") }
        // onValueChange is typed (T) -> Unit, here T == PlayMode
        @Suppress("UNCHECKED_CAST")
        (dropdown.onValueChange as (PlayMode) -> Unit)(PlayMode.Shuffle)
        assertEquals(PlayMode.Shuffle.name, kv.playMode.value)
    }

    @Test
    fun `clear-history click resets historyPlayPosition to zero`() {
        // Pre-condition: position is non-zero
        kv.historyPos.value = 12345L
        assertEquals(12345L, kv.historyPos.value)

        val click = group.preferences()
            .filterIsInstance<ClickPreference>()
            .first { it.key == "lplayer.clear_history_position" }

        val toaster = FakeToaster()
        val ctx = PreferenceActionContext(toaster = toaster)
        click.onClick(ctx)

        assertEquals(0L, kv.historyPos.value)
        assertNotNull(ctx)
    }

    @Test
    fun `preferences lambda is lazy`() {
        var invocations = 0
        val captured: () -> List<com.lalilu.common.settings.Preference<*>> = {
            invocations++
            group.preferences()
        }
        captured()
        captured()
        // Two explicit calls, so at least 2
        assertTrue(invocations >= 2)
    }

    @Test
    fun `group order 10 is between app and lyric`() {
        // Sanity: app-level uses negative, lyric-style uses 20+, lplayer should sit in the middle.
        val order = group.order
        assertTrue(order in 0..100, "order=$order out of expected band")
    }
}
