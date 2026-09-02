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

package com.lalilu.llyricview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.common.kv.testing.TestKVContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * LyricSettings 数据模型与序列化回环测试：
 * - JSON 回环（含自定义序列化器：TextAlign / Dp / TextUnit / PaddingValues）
 * - KV 回环（InMemoryKVSaver）：save() 后恢复一致；未 save 不落盘
 * - 缺键回落默认值（旧数据兼容）
 */
class LyricSettingsSerializationTest {

    private class TestLyricKV(saver: KVSaver) : TestKVContext("lyric_settings_test", saver) {
        val settings = obtain<LyricSettings>("LyricSettings", LyricSettings())
            .apply { disableAutoSave() }
    }

    private val store: MutableMap<String, Any?> = mutableMapOf()
    private lateinit var saver: InMemoryKVSaver
    private lateinit var kv: TestLyricKV

    private val sample = LyricSettings(
        textAlign = TextAlign.Center,
        containerPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        gapSize = 12.dp,
        scaleStart = 0.8f,
        scaleEnd = 1.05f,
        timeOffset = 120L,
        mainFontSize = 40.sp,
        mainLineHeight = 52.sp,
        mainFontWeight = FontWeight.Black.weight,
        translationFontSize = 20.sp,
        translationLineHeight = 34.sp,
        translationFontWeight = FontWeight.SemiBold.weight,
        blurEffectEnable = false,
        reducedTransitionEnabled = true,
        translationVisible = false,
        onlyCurrentTranslationVisible = true,
        scrollSpringStiffness = 130f,
        scrollSpringDampingRatio = 0.6f,
    )

    @BeforeTest
    fun setup() {
        saver = InMemoryKVSaver(store)
        KVContext.registerSaver(saver)
        startKoin {
            modules(module { single { Json { ignoreUnknownKeys = true } } })
        }
        kv = TestLyricKV(saver)
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        KVContext.kvMap.clear()
    }

    @Test
    fun `json round trip preserves all fields`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<LyricSettings>(json.encodeToString(sample))
        assertEquals(sample, decoded)
    }

    @Test
    fun `kv round trip restores saved settings`() {
        kv.settings.value = sample
        kv.settings.save()

        KVContext.kvMap.clear()
        val restored = TestLyricKV(InMemoryKVSaver(store)).settings.value
        assertEquals(sample, restored)
    }

    @Test
    fun `settings not persisted without explicit save`() {
        kv.settings.value = sample

        KVContext.kvMap.clear()
        val restored = TestLyricKV(InMemoryKVSaver(store)).settings.value
        assertEquals(LyricSettings(), restored)
    }

    @Test
    fun `missing keys fall back to defaults`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<LyricSettings>("{}")
        assertEquals(LyricSettings(), decoded)
    }
}
