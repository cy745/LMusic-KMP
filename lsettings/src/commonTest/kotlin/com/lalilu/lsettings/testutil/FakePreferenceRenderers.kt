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

package com.lalilu.lsettings.testutil

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.common.settings.AccordionPreference
import com.lalilu.common.settings.ClickPreference
import com.lalilu.common.settings.DropdownPreference
import com.lalilu.common.settings.MultiSelectPreference
import com.lalilu.common.settings.Preference
import com.lalilu.common.settings.SliderPreference
import com.lalilu.common.settings.SwitchPreference
import com.lalilu.common.settings.TextPreference
import com.lalilu.lsettings.component.PreferenceRenderers


/**
 * 用于测试的"假"渲染器：只记录调用的类型，不真正执行 Composable。
 *
 * 业务侧不要使用。
 */
class FakePreferenceRenderers : PreferenceRenderers {
    val seenTypes: MutableList<Class<out Preference<*>>> = mutableListOf()
    var customRenderInvocations: Int = 0
        private set

    @Composable
    override fun renderSwitch(pref: SwitchPreference, modifier: Modifier) { seenTypes += pref::class.java }
    @Composable
    override fun renderSlider(pref: SliderPreference, modifier: Modifier) { seenTypes += pref::class.java }
    @Composable
    override fun renderDropdown(pref: DropdownPreference<*>, modifier: Modifier) { seenTypes += pref::class.java }
    @Composable
    override fun renderMultiSelect(pref: MultiSelectPreference<*>, modifier: Modifier) { seenTypes += pref::class.java }
    @Composable
    override fun renderText(pref: TextPreference, modifier: Modifier) { seenTypes += pref::class.java }
    @Composable
    override fun renderAccordion(pref: AccordionPreference, modifier: Modifier) { seenTypes += pref::class.java }
    @Composable
    override fun renderClick(pref: ClickPreference, modifier: Modifier) { seenTypes += pref::class.java }

    @Composable
    override fun renderUnknown(pref: Preference<*>, modifier: Modifier) { seenTypes += pref::class.java }
}
