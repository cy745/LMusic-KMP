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

package com.lalilu.lsettings.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.lalilu.common.settings.ClickPreference
import com.lalilu.common.settings.CustomPreference
import com.lalilu.common.settings.DropdownPreference
import com.lalilu.common.settings.MultiSelectPreference
import com.lalilu.common.settings.Preference
import com.lalilu.common.settings.SliderPreference
import com.lalilu.common.settings.SwitchPreference
import com.lalilu.common.settings.TextPreference
import com.lalilu.common.settings.newPreferenceRowScope
import com.lalilu.lsettings.component.preferences.ClickPreferenceRow
import com.lalilu.lsettings.component.preferences.DropdownPreferenceRow
import com.lalilu.lsettings.component.preferences.MultiSelectPreferenceRow
import com.lalilu.lsettings.component.preferences.SliderPreferenceRow
import com.lalilu.lsettings.component.preferences.SwitchPreferenceRow
import com.lalilu.lsettings.component.preferences.TextPreferenceRow


/**
 * "如何把 [Preference] 渲染成 UI"的抽象。
 *
 * ## 为什么需要它
 *
 * 1. **可测试性**：测试时可注入一个 [FakePreferenceRenderers]（参见
 *    `lsettings/src/commonTest/.../testutil/FakePreferenceRenderers.kt`），
 *    只记录分发类型不真正执行 Composable，从而在 JVM 单元测试里
 *    验证 `DefaultPreferenceRegistry` 的分发逻辑。
 * 2. **可替换**：未来若需要 iOS / Wear OS / 自定义 Material You 主题，
 *    只需替换默认实现，无需修改业务模块的偏好定义。
 *
 * 默认实现见 [DefaultPreferenceRenderers]，使用本模块内的 Material3 Row 组件。
 */
interface PreferenceRenderers {
    @Composable
    fun renderSwitch(pref: SwitchPreference, modifier: Modifier)

    @Composable
    fun renderSlider(pref: SliderPreference, modifier: Modifier)

    @Composable
    fun renderDropdown(pref: DropdownPreference<*>, modifier: Modifier)

    @Composable
    fun renderMultiSelect(pref: MultiSelectPreference<*>, modifier: Modifier)

    @Composable
    fun renderText(pref: TextPreference, modifier: Modifier)

    @Composable
    fun renderClick(pref: ClickPreference, modifier: Modifier)

    /** 兜底渲染：未知子类或不支持的类型。 */
    @Composable
    fun renderUnknown(pref: Preference<*>, modifier: Modifier) {
        androidx.compose.material3.Text(
            text = "Unsupported preference: ${pref::class.simpleName}",
            modifier = modifier
        )
    }
}


/**
 * 把任意 [Preference] 分派到对应渲染器。
 *
 * - 若 pref 是 [CustomPreference] → 走其自定义 `content`
 * - 否则按类型分派到 [PreferenceRenderers] 的对应方法
 *
 * 该函数是 [com.lalilu.lsettings.SettingsScreenContent] 渲染偏好项的唯一入口。
 */
@Composable
fun PreferenceRenderers.render(
    pref: Preference<*>,
    modifier: Modifier = Modifier,
) {
    when (pref) {
        is CustomPreference<*>       -> pref.content(
            newPreferenceRowScope(pref),
            pref
        )
        is SwitchPreference          -> renderSwitch(pref, modifier)
        is SliderPreference          -> renderSlider(pref, modifier)
        is DropdownPreference<*>     -> renderDropdown(pref, modifier)
        is MultiSelectPreference<*>  -> renderMultiSelect(pref, modifier)
        is TextPreference            -> renderText(pref, modifier)
        is ClickPreference           -> renderClick(pref, modifier)
    }
}


/**
 * 默认 Material3 渲染实现：每个 [Preference] 子类对应一个 [com.lalilu.lsettings.component.preferences]
 * 包内的 Row 组件。
 */
class DefaultPreferenceRenderers : PreferenceRenderers {
    @Composable
    override fun renderSwitch(pref: SwitchPreference, modifier: Modifier) = SwitchPreferenceRow(pref, modifier)

    @Composable
    override fun renderSlider(pref: SliderPreference, modifier: Modifier) = SliderPreferenceRow(pref, modifier)

    @Composable
    override fun renderDropdown(pref: DropdownPreference<*>, modifier: Modifier) = DropdownPreferenceRow(pref, modifier)

    @Composable
    override fun renderMultiSelect(pref: MultiSelectPreference<*>, modifier: Modifier) = MultiSelectPreferenceRow(pref, modifier)

    @Composable
    override fun renderText(pref: TextPreference, modifier: Modifier) = TextPreferenceRow(pref, modifier)

    @Composable
    override fun renderClick(pref: ClickPreference, modifier: Modifier) = ClickPreferenceRow(pref, modifier)
}


/**
 * `CompositionLocal` 形式的 [PreferenceRenderers] 注入点。
 *
 * 默认值为 [DefaultPreferenceRenderers]；测试 / 预览可在 [androidx.compose.runtime.CompositionLocalProvider]
 * 中提供 [FakePreferenceRenderers] 等替身。
 */
val LocalPreferenceRenderers = staticCompositionLocalOf<PreferenceRenderers> {
    DefaultPreferenceRenderers()
}
