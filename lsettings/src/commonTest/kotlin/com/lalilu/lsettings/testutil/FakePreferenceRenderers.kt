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

package com.lalilu.lsettings.testutil

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    override fun renderClick(pref: ClickPreference, modifier: Modifier) { seenTypes += pref::class.java }

    @Composable
    override fun renderUnknown(pref: Preference<*>, modifier: Modifier) { seenTypes += pref::class.java }
}
