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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector


/**
 * 设置项的根接口。
 *
 * 仿照 Android Preference 的设计：一个 [Preference] 对应一个可配置项，
 * 绑定一个值并提供 onValueChange 回调，由 settings 系统负责渲染。
 *
 * ## 反应式
 *
 * [value] 与 [onValueChange] **共同**构成状态来源，并且**都是反应式的**：
 *
 * - [value] 内部包装了一个 [MutableState]，所以 Compose 在读取 `pref.value`
 *   时会建立快照订阅，state 变化时自动触发重组；
 * - [onValueChange] 既更新内部 state（驱动 UI 立即刷新）又调用 `writeBack`
 *   写穿到 [com.lalilu.common.kv.KVItem] / 业务方提供的副作用；
 * - 业务模块用 DSL `switch(kv = ...) { ... }` 形式创建时，会自动建立
 *   `state ↔ kv` 的双向通道。
 *
 * ## UI 字段
 *
 * [title] / [summary] / [icon] 都是 `@Composable () -> ...` 形式的 lambda，
 * 方便子模块注入 stringResource / iconResource 等动态资源，
 * 同时保持本接口不依赖 Material3。
 *
 * ## 条件联动
 *
 * [visible] / [enabled] 默认恒为 `true`，预留扩展位。
 *
 * @param T 偏好项承载的值类型。`Unit` 仅用于 [ClickPreference]。
 */
sealed interface Preference<T> {
    /** 在所属 [SettingsGroup] 内的唯一键。 */
    val key: String

    /** 标题，由 Composable 上下文解析，便于访问 stringResource。 */
    val title: @Composable () -> String

    /** 副标题（可空），同样在 Composable 中求值。 */
    val summary: @Composable () -> String?

    /** 左侧图标（可空）。 */
    val icon: @Composable () -> ImageVector?

    /**
     * 当前值。getter 形式——子类从内部 [MutableState] 实时读取，
     * 读取行为本身即建立 Compose 快照订阅。
     */
    val value: T

    /**
     * 写值入口。实现约定：先更新内部 state（保证 UI 立即重组），
     * 再调用 `writeBack` 把新值写回持久化层或外部副作用。
     */
    val onValueChange: (T) -> Unit

    /** 可见性判定；返回 `false` 时该行不渲染。默认 `true`。 */
    val visible: () -> Boolean

    /** 可用性判定；返回 `false` 时该行仍渲染但禁用交互。默认 `true`。 */
    val enabled: () -> Boolean
}


/**
 * Switch 类型的偏好项。
 *
 * 内部用 [MutableState] 持有当前 Boolean 状态；DSL 通过 [mutableStateOf]
 * 包装 `KVItem.value` 的初始值，并在 [onValueChange] 中既更新 state 又
 * 回写 KV。Row 组件读取 [value] 时会建立快照订阅，开关切换即时可见。
 *
 * ## 两种构造形式
 *
 * - **主构造器**：直接接收 [state] / [writeBack] / 元数据，DSL 走这条路径；
 * - **次构造器**：仅接收 `value` / `onValueChange` 简单形式，测试代码用。
 *
 * @param state 内部响应式容器，由 [mutableStateOf] 创建
 * @param writeBack 写穿回调，DSL 中绑定到 `kv.value = it`
 */
class SwitchPreference(
    private val state: MutableState<Boolean>,
    private val writeBack: (Boolean) -> Unit,
    override val key: String,
    override val title: @Composable () -> String,
    override val summary: @Composable () -> String? = { null },
    override val icon: @Composable () -> ImageVector? = { null },
    override val visible: () -> Boolean = { true },
    override val enabled: () -> Boolean = { true }
) : Preference<Boolean> {

    /** 测试 / 简单场景便捷构造。 */
    constructor(
        key: String,
        title: @Composable () -> String,
        value: Boolean,
        onValueChange: (Boolean) -> Unit,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ) : this(
        state = mutableStateOf(value),
        writeBack = onValueChange,
        key = key,
        title = title,
        summary = summary,
        icon = icon,
        visible = visible,
        enabled = enabled
    )

    override val value: Boolean get() = state.value
    override val onValueChange: (Boolean) -> Unit = { newValue ->
        state.value = newValue
        writeBack(newValue)
    }
}


/**
 * Slider 类型的偏好项。
 *
 * 与 [SwitchPreference] 同样的"双源"反应式设计，差异在 [value] 承载 [Float]
 * 以及额外的 [valueRange] / [steps] / [valueLabel] 字段。
 */
class SliderPreference(
    private val state: MutableState<Float>,
    private val writeBack: (Float) -> Unit,
    val valueRange: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val valueLabel: @Composable (Float) -> String,
    override val key: String,
    override val title: @Composable () -> String,
    override val summary: @Composable () -> String? = { null },
    override val icon: @Composable () -> ImageVector? = { null },
    override val visible: () -> Boolean = { true },
    override val enabled: () -> Boolean = { true }
) : Preference<Float> {

    constructor(
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
    ) : this(
        state = mutableStateOf(value),
        writeBack = onValueChange,
        valueRange = valueRange,
        steps = steps,
        valueLabel = valueLabel,
        key = key,
        title = title,
        summary = summary,
        icon = icon,
        visible = visible,
        enabled = enabled
    )

    override val value: Float get() = state.value
    override val onValueChange: (Float) -> Unit = { newValue ->
        state.value = newValue
        writeBack(newValue)
    }
}


/**
 * 单选下拉。
 *
 * 业务对象 [T] 与持久化 String 之间通过 [serialize] / [deserialize] 互转，
 * 这样设计可以容忍枚举字段重命名 / 顺序调整等破坏性变更。
 *
 * @param T 业务类型（通常是枚举或 sealed 子类）
 * @param options 所有可选项
 * @param optionLabel 单项展示文本
 * @param serialize 写入持久化前的字符串化
 * @param deserialize 读出持久化后的反字符串化；返回 `null` 时回退到 `fallback`
 */
class DropdownPreference<T : Any>(
    private val state: MutableState<T>,
    private val writeBack: (T) -> Unit,
    val options: List<T>,
    val optionLabel: (T) -> String,
    val serialize: (T) -> String,
    val deserialize: (String) -> T?,
    override val key: String,
    override val title: @Composable () -> String,
    override val summary: @Composable () -> String? = { null },
    override val icon: @Composable () -> ImageVector? = { null },
    override val visible: () -> Boolean = { true },
    override val enabled: () -> Boolean = { true }
) : Preference<T> {

    constructor(
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
    ) : this(
        state = mutableStateOf(selectedValue),
        writeBack = onValueChange,
        options = options,
        optionLabel = optionLabel,
        serialize = serialize,
        deserialize = deserialize,
        key = key,
        title = title,
        summary = summary,
        icon = icon,
        visible = visible,
        enabled = enabled
    )

    override val value: T get() = state.value
    override val onValueChange: (T) -> Unit = { newValue ->
        state.value = newValue
        writeBack(newValue)
    }
}


/**
 * 多选下拉。
 *
 * 与 [DropdownPreference] 同样依赖 [serializeSelected] / [deserializeSelected]
 * 在 [T] 与 `List<String>`（持久化）之间互转。
 */
class MultiSelectPreference<T : Any>(
    private val state: MutableState<Set<T>>,
    private val writeBack: (Set<T>) -> Unit,
    val options: List<T>,
    val optionLabel: (T) -> String,
    val serializeSelected: (T) -> String,
    val deserializeSelected: (String) -> T?,
    override val key: String,
    override val title: @Composable () -> String,
    override val summary: @Composable () -> String? = { null },
    override val icon: @Composable () -> ImageVector? = { null },
    override val visible: () -> Boolean = { true },
    override val enabled: () -> Boolean = { true }
) : Preference<Set<T>> {

    constructor(
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
    ) : this(
        state = mutableStateOf(selectedValues),
        writeBack = onValueChange,
        options = options,
        optionLabel = optionLabel,
        serializeSelected = serializeSelected,
        deserializeSelected = deserializeSelected,
        key = key,
        title = title,
        summary = summary,
        icon = icon,
        visible = visible,
        enabled = enabled
    )

    override val value: Set<T> get() = state.value
    override val onValueChange: (Set<T>) -> Unit = { newValue ->
        state.value = newValue
        writeBack(newValue)
    }
}


/**
 * 文本输入偏好项。
 *
 * @param singleLine 是否单行；为 `false` 时输入框可换行
 * @param hint 占位提示（可空），仅在值为空时显示
 */
class TextPreference(
    private val state: MutableState<String>,
    private val writeBack: (String) -> Unit,
    val singleLine: Boolean,
    val hint: @Composable () -> String?,
    override val key: String,
    override val title: @Composable () -> String,
    override val summary: @Composable () -> String? = { null },
    override val icon: @Composable () -> ImageVector? = { null },
    override val visible: () -> Boolean = { true },
    override val enabled: () -> Boolean = { true }
) : Preference<String> {

    constructor(
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
    ) : this(
        state = mutableStateOf(value),
        writeBack = onValueChange,
        singleLine = singleLine,
        hint = hint,
        key = key,
        title = title,
        summary = summary,
        icon = icon,
        visible = visible,
        enabled = enabled
    )

    override val value: String get() = state.value
    override val onValueChange: (String) -> Unit = { newValue ->
        state.value = newValue
        writeBack(newValue)
    }
}


/**
 * 可点击偏好项（如"清除缓存"）。
 *
 * 没有可持久化的值——[value] 恒为 [Unit]；[onValueChange] 也不应有副作用。
 * 真正的逻辑通过 [onClick] 触发，回调拿到一个 [PreferenceActionContext]
 * 用来发 Toaster / 跳转 / 调业务方法。
 */
class ClickPreference(
    val onClick: (PreferenceActionContext) -> Unit,
    override val key: String,
    override val title: @Composable () -> String,
    override val summary: @Composable () -> String? = { null },
    override val icon: @Composable () -> ImageVector? = { null },
    override val visible: () -> Boolean = { true },
    override val enabled: () -> Boolean = { true }
) : Preference<Unit> {
    override val value: Unit = Unit
    override val onValueChange: (Unit) -> Unit = {}
}


/**
 * 自定义渲染偏好项。
 *
 * 适合走默认 Material3 之外 UI 的场景（如内嵌 Slider 列表、调用图等）。
 * [content] 拿到 [PreferenceRowScope]（含 `set` 等便利方法）作为 receiver。
 */
class CustomPreference<T>(
    private val state: MutableState<T>,
    private val writeBack: (T) -> Unit,
    val content: @Composable PreferenceRowScope.(Preference<*>) -> Unit,
    override val key: String,
    override val title: @Composable () -> String,
    override val summary: @Composable () -> String? = { null },
    override val icon: @Composable () -> ImageVector? = { null },
    override val visible: () -> Boolean = { true },
    override val enabled: () -> Boolean = { true }
) : Preference<T> {

    constructor(
        key: String,
        title: @Composable () -> String,
        value: T,
        onValueChange: (T) -> Unit,
        content: @Composable PreferenceRowScope.(Preference<*>) -> Unit,
        summary: @Composable () -> String? = { null },
        icon: @Composable () -> ImageVector? = { null },
        visible: () -> Boolean = { true },
        enabled: () -> Boolean = { true }
    ) : this(
        state = mutableStateOf(value),
        writeBack = onValueChange,
        content = content,
        key = key,
        title = title,
        summary = summary,
        icon = icon,
        visible = visible,
        enabled = enabled
    )

    override val value: T get() = state.value
    override val onValueChange: (T) -> Unit = { newValue ->
        state.value = newValue
        writeBack(newValue)
    }
}
