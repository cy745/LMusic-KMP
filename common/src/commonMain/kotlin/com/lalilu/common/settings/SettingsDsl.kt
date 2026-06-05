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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.lalilu.common.kv.KVItem


/**
 * 设置页 DSL 入口。
 *
 * 用法：
 * ```
 * @Factory
 * @Named("settings_lplayer")
 * fun provideLPlayerSettings(): SettingsGroup = settingsGroup(
 *     key = "lplayer", order = 10, title = { "播放器" },
 * ) {
 *     switch(LPlayerKV.autoPlayWhenRestart, title = { "启动后自动播放" })
 *     dropdown(LPlayerKV.playMode, ...)
 *     click("lplayer.clear_queue", title = { "清空队列" }, onClick = { it.toaster.info("已清空") })
 * }
 * ```
 *
 * @param key 全局唯一标识
 * @param order 渲染顺序（升序），建议 app 级配置用负值
 * @param title 标题 lambda
 * @param description 描述 lambda（可空）
 * @param block DSL 块，调用 builder 的 switch/slider/... 方法
 */
fun settingsGroup(
    key: String,
    order: Int = 0,
    title: @Composable () -> String? = { null },
    description: @Composable () -> String? = { null },
    block: SettingsGroupBuilder.() -> Unit
): SettingsGroup {
    val list = mutableListOf<Preference<*>>()
    val builder = SettingsGroupBuilderImpl(list)
    builder.block()
    return SettingsGroup(
        key = key,
        order = order,
        title = title,
        description = description,
        preferences = { list.toList() }
    )
}


/** 标记设置系统 DSL 块，防止外部作用域的 `add`/`switch` 等函数意外捕获。 */
@DslMarker
annotation class SettingsDsl


/**
 * 设置 DSL 的 builder 接口。常见用法是直接用 `settingsGroup { ... }` 块。
 *
 * 业务模块贡献的 [SettingsGroup] 都通过本接口的方法声明偏好项。
 */
@SettingsDsl
interface SettingsGroupBuilder {
    fun <T : Preference<*>> add(preference: T): T

    // region --- Switch ---

    /** 测试 / 简单场景：直接传 `value` + `onValueChange`。 */
    fun switch(
        key: String,
        title: @Composable () -> String,
        value: Boolean,
        onValueChange: (Boolean) -> Unit,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): SwitchPreference

    /** 生产场景：与 `KVItem<Boolean>` 双向绑定，自动建立 `state ↔ kv` 通道。 */
    fun switch(
        kv: KVItem<Boolean>,
        title: @Composable () -> String,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): SwitchPreference

    // endregion

    // region --- Slider ---

    fun slider(
        key: String,
        title: @Composable () -> String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        steps: Int = 0,
        valueLabel: @Composable (Float) -> String = { it.toString() },
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): SliderPreference

    fun slider(
        kv: KVItem<Float>,
        title: @Composable () -> String,
        valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        steps: Int = 0,
        valueLabel: @Composable (Float) -> String = { it.toString() },
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): SliderPreference

    // endregion

    // region --- Dropdown ---

    fun <T : Any> dropdown(
        key: String,
        title: @Composable () -> String,
        selectedValue: T,
        options: List<T>,
        optionLabel: (T) -> String,
        onValueChange: (T) -> Unit,
        serialize: (T) -> String,
        deserialize: (String) -> T?,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): DropdownPreference<T>

    fun <T : Any> dropdown(
        kv: KVItem<String>,
        title: @Composable () -> String,
        options: List<T>,
        optionLabel: (T) -> String,
        serialize: (T) -> String,
        deserialize: (String) -> T?,
        fallback: T,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): DropdownPreference<T>

    // endregion

    // region --- MultiSelect ---

    fun <T : Any> multiSelect(
        key: String,
        title: @Composable () -> String,
        selectedValues: Set<T>,
        options: List<T>,
        optionLabel: (T) -> String,
        onValueChange: (Set<T>) -> Unit,
        serializeSelected: (T) -> String,
        deserializeSelected: (String) -> T?,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): MultiSelectPreference<T>

    fun <T : Any> multiSelect(
        kv: KVItem<List<String>>,
        title: @Composable () -> String,
        options: List<T>,
        optionLabel: (T) -> String,
        serializeSelected: (T) -> String,
        deserializeSelected: (String) -> T?,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): MultiSelectPreference<T>

    // endregion

    // region --- Text / Click / Custom ---

    fun text(
        key: String,
        title: @Composable () -> String,
        value: String,
        onValueChange: (String) -> Unit,
        singleLine: Boolean = true,
        hint: @Composable () -> String? = { null },
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): TextPreference

    fun text(
        kv: KVItem<String>,
        title: @Composable () -> String,
        singleLine: Boolean = true,
        hint: @Composable () -> String? = { null },
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): TextPreference

    fun click(
        key: String,
        title: @Composable () -> String,
        onClick: (PreferenceActionContext) -> Unit,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): ClickPreference

    fun <T> custom(
        key: String,
        title: @Composable () -> String,
        value: T,
        onValueChange: (T) -> Unit,
        content: @Composable PreferenceRowScope.(Preference<*>) -> Unit,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ): CustomPreference<T>

    // endregion
}


/**
 * [SettingsGroupBuilder] 的实现：把所有 add 进来的 [Preference] 收集到 [list]。
 *
 * `internal` —— 业务模块不会直接 new 它，只通过 `settingsGroup { ... }`
 * 工厂方法间接使用。
 */
internal class SettingsGroupBuilderImpl(
    private val list: MutableList<Preference<*>>
) : SettingsGroupBuilder {

    override fun <T : Preference<*>> add(preference: T): T {
        list += preference
        return preference
    }

    // region Switch

    override fun switch(
        key: String,
        title: @Composable () -> String,
        value: Boolean,
        onValueChange: (Boolean) -> Unit,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): SwitchPreference = add(
        SwitchPreference(
            key = key, title = title, value = value, onValueChange = onValueChange,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    /**
     * KV 绑定的 switch：内部用 [mutableStateOf] 包装初始值，
     * 并把 `writeBack` 指向 `kv.value = it`，构成"state ↔ kv"双向通道。
     */
    override fun switch(
        kv: KVItem<Boolean>,
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): SwitchPreference = add(
        SwitchPreference(
            state = mutableStateOf(kv.value),
            writeBack = { kv.value = it },
            key = kv.key,
            title = title,
            summary = summary,
            icon = icon,
            visible = visible,
            enabled = enabled
        )
    )

    // endregion

    // region Slider

    override fun slider(
        key: String,
        title: @Composable () -> String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        valueLabel: @Composable (Float) -> String,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): SliderPreference = add(
        SliderPreference(
            key = key, title = title, value = value, onValueChange = onValueChange,
            valueRange = valueRange, steps = steps, valueLabel = valueLabel,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    override fun slider(
        kv: KVItem<Float>,
        title: @Composable () -> String,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        valueLabel: @Composable (Float) -> String,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): SliderPreference = add(
        SliderPreference(
            state = mutableStateOf(kv.value),
            writeBack = { kv.value = it },
            valueRange = valueRange,
            steps = steps,
            valueLabel = valueLabel,
            key = kv.key,
            title = title,
            summary = summary,
            icon = icon,
            visible = visible,
            enabled = enabled
        )
    )

    // endregion

    // region Dropdown

    override fun <T : Any> dropdown(
        key: String,
        title: @Composable () -> String,
        selectedValue: T,
        options: List<T>,
        optionLabel: (T) -> String,
        onValueChange: (T) -> Unit,
        serialize: (T) -> String,
        deserialize: (String) -> T?,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): DropdownPreference<T> = add(
        DropdownPreference(
            key = key, title = title, selectedValue = selectedValue,
            options = options, optionLabel = optionLabel, onValueChange = onValueChange,
            serialize = serialize, deserialize = deserialize,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    override fun <T : Any> dropdown(
        kv: KVItem<String>,
        title: @Composable () -> String,
        options: List<T>,
        optionLabel: (T) -> String,
        serialize: (T) -> String,
        deserialize: (String) -> T?,
        fallback: T,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): DropdownPreference<T> = add(
        DropdownPreference(
            state = mutableStateOf(deserialize(kv.value) ?: fallback),
            writeBack = { kv.value = serialize(it) },
            options = options,
            optionLabel = optionLabel,
            serialize = serialize,
            deserialize = deserialize,
            key = kv.key,
            title = title,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    // endregion

    // region MultiSelect

    override fun <T : Any> multiSelect(
        key: String,
        title: @Composable () -> String,
        selectedValues: Set<T>,
        options: List<T>,
        optionLabel: (T) -> String,
        onValueChange: (Set<T>) -> Unit,
        serializeSelected: (T) -> String,
        deserializeSelected: (String) -> T?,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): MultiSelectPreference<T> = add(
        MultiSelectPreference(
            key = key, title = title, selectedValues = selectedValues,
            options = options, optionLabel = optionLabel, onValueChange = onValueChange,
            serializeSelected = serializeSelected, deserializeSelected = deserializeSelected,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    override fun <T : Any> multiSelect(
        kv: KVItem<List<String>>,
        title: @Composable () -> String,
        options: List<T>,
        optionLabel: (T) -> String,
        serializeSelected: (T) -> String,
        deserializeSelected: (String) -> T?,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): MultiSelectPreference<T> = add(
        MultiSelectPreference(
            state = mutableStateOf(kv.value.mapNotNull(deserializeSelected).toSet()),
            writeBack = { kv.value = it.map(serializeSelected) },
            options = options,
            optionLabel = optionLabel,
            serializeSelected = serializeSelected,
            deserializeSelected = deserializeSelected,
            key = kv.key,
            title = title,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    // endregion

    // region Text / Click / Custom

    override fun text(
        key: String,
        title: @Composable () -> String,
        value: String,
        onValueChange: (String) -> Unit,
        singleLine: Boolean,
        hint: @Composable () -> String?,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): TextPreference = add(
        TextPreference(
            key = key, title = title, value = value, onValueChange = onValueChange,
            singleLine = singleLine, hint = hint,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    override fun text(
        kv: KVItem<String>,
        title: @Composable () -> String,
        singleLine: Boolean,
        hint: @Composable () -> String?,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): TextPreference = add(
        TextPreference(
            state = mutableStateOf(kv.value),
            writeBack = { kv.value = it },
            singleLine = singleLine,
            hint = hint,
            key = kv.key,
            title = title,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    override fun click(
        key: String,
        title: @Composable () -> String,
        onClick: (PreferenceActionContext) -> Unit,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): ClickPreference = add(
        ClickPreference(
            onClick = onClick,
            key = key,
            title = title,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    override fun <T> custom(
        key: String,
        title: @Composable () -> String,
        value: T,
        onValueChange: (T) -> Unit,
        content: @Composable PreferenceRowScope.(Preference<*>) -> Unit,
        summary: @Composable () -> String?,
        icon: @Composable () -> ImageVector?,
        visible: () -> Boolean,
        enabled: () -> Boolean
    ): CustomPreference<T> = add(
        CustomPreference(
            key = key, title = title, value = value, onValueChange = onValueChange,
            content = content,
            summary = summary, icon = icon, visible = visible, enabled = enabled
        )
    )

    // endregion
}
