# 设置系统（Preference System）架构

LMusic-KMP 的设置系统仿照 Android `Preference` 设计，
让各业务模块以"插件式"方式贡献设置项，由 `:lsettings` 统一收集并渲染。

本文档面向**新加入业务的开发者**与**未来维护者**，解释系统结构与扩展方式。

---

## 1. 模块拓扑

```
common   (无 UI, 定义接口与 DSL)
   ↑ api
component (Material3 基础组件, 仍无 settings 概念)
   ↑ api
lsettings  (新模块, 路由 + 收集 + Material3 渲染)
   ↑ implementation
composeApp (顶层入口, 注册 /settings 路由)
   ↑
业务模块   (lplayer / lhome / lhistory / lartist / lplaylist / lalbum)
           → 通过 @Factory + @Named("settings_xxx") 注入 SettingsGroup
```

**关键解耦**：

- `:lsettings` **不依赖**任何业务模块；它只通过 Koin Named 拉取 `SettingsGroup`
- 业务模块**不感知** `:lsettings` 的存在；按惯例把 `SettingsGroup` 注入 Koin 即可
- 任何业务模块的修改都不会触发 `:lsettings` 重编（编译边界清晰）

---

## 2. 核心抽象（`common` 包）

| 类型                 | 文件                            | 角色                         |
|--------------------|--------------------------------|----------------------------|
| `Preference<T>`     | `settings/Preference.kt`        | 设置项根接口，7 个 sealed 子类    |
| `SettingsGroup`     | `settings/SettingsGroup.kt`     | 一组偏好项的容器（含 order/title） |
| `SettingsGroupBuilder` | `settings/SettingsDsl.kt`    | DSL 构造器                  |
| `settingsGroup { ... }` | `settings/SettingsDsl.kt`   | DSL 入口函数                |
| `PreferenceRowScope` | `settings/PreferenceRowScope.kt` | 自定义渲染的 receiver     |
| `PreferenceActionContext` | `settings/PreferenceActionContext.kt` | Click 偏好项的运行时上下文 |
| `SettingsCollector`  | `settings/SettingsCollector.kt` | 拉取所有 group 入口     |
| `ToasterLike`        | `settings/PreferenceActionContext.kt` | 轻量 Toaster 接口     |

### 2.1 反应式 value

每个 `Preference<T>` 子类的 `value` 是 **getter**，内部从 `MutableState<T>` 读取：

```kotlin
class SwitchPreference(
    private val state: MutableState<Boolean>,
    private val writeBack: (Boolean) -> Unit,
    ...
) : Preference<Boolean> {
    override val value: Boolean get() = state.value
    override val onValueChange: (Boolean) -> Unit = { newValue ->
        state.value = newValue   // 立即触发 UI 重组
        writeBack(newValue)       // 回写 KV / 业务副作用
    }
}
```

DSL 在创建时 `mutableStateOf(kv.value)`，并把 `writeBack` 指向 `kv.value = it`，
形成 "state ↔ kv" 的双向通道。Row 组件读取 `pref.value` 自动订阅 state，
**改完即时可见**，无需退出页面。

### 2.2 支持的偏好类型

| 子类                  | 用途                       | 典型 KV 绑定字段                |
|----------------------|---------------------------|-----------------------------|
| `SwitchPreference`   | 开关                       | Boolean KV                |
| `SliderPreference`   | 滑块                       | Float KV (with range/steps)|
| `DropdownPreference<T>` | 单选下拉                  | String KV（serialize/deserialize）|
| `MultiSelectPreference<T>` | 多选下拉               | `List<String>` KV          |
| `TextPreference`     | 文本输入                    | String KV                  |
| `ClickPreference`    | 触发回调                    | 无（写副作用）              |
| `CustomPreference<T>` | 完全自定义 Composable     | 任意 KV                    |

---

## 3. 业务模块贡献（lplayer 范本）

```kotlin
// lplayer/src/commonMain/kotlin/com/lalilu/lplayer/LPlayerSettings.kt
@Factory
@Named("settings_lplayer")
fun provideLPlayerSettings(): SettingsGroup = settingsGroup(
    key = "lplayer",
    order = 10,                       // 渲染顺序：app 级 -1000 / 业务 0-100
    title = { "播放器" },
) {
    switch(
        kv = LPlayerKV.autoPlayWhenRestart,
        title = { "启动后自动播放" },
        summary = { "应用启动后自动恢复上次的播放状态" }
    )
    dropdown(
        kv = LPlayerKV.playMode,
        title = { "播放模式" },
        options = PlayMode.entries,
        optionLabel = { mode -> /*...*/ },
        serialize = { it.name },
        deserialize = { name -> PlayMode.from(name) },
        fallback = PlayMode.ListRecycle
    )
    click(
        key = "lplayer.clear_history_position",
        title = { "清除播放进度记录" },
        onClick = { ctx -> LPlayerKV.historyPlayPosition.value = 0L }
    )
}
```

约定：
- 一个业务模块 = 一个 `SettingsGroup`（key 唯一）
- `order` 默认 0；`app` 级别建议 `-1000`、具体业务 `0-100`
- Click 偏好的 key 应以 `<module>.` 前缀（如 `lplayer.clear_queue`）

---

## 4. 顶层渲染（`:lsettings`）

`SettingsScreen` 通过 `SettingsViewModel` 一次性拉取所有 `SettingsGroup`，
`SettingsScreenContent` 用 `LazyColumn` 渲染：每个 group 一个 header + 一组 preference rows。

`PreferenceRenderers` 抽象允许**测试时**注入 `FakePreferenceRenderers` 拦截分发，
**生产时**用 `DefaultPreferenceRenderers`（Material3 Row 组件）。

```
PreferenceRenderers (interface)
  ├─ renderSwitch(SwitchPreference)         → SwitchPreferenceRow
  ├─ renderSlider(SliderPreference)         → SliderPreferenceRow
  ├─ renderDropdown(DropdownPreference<*>)  → DropdownPreferenceRow
  ├─ renderMultiSelect(...)                 → MultiSelectPreferenceRow
  ├─ renderText(TextPreference)             → TextPreferenceRow
  └─ renderClick(ClickPreference)           → ClickPreferenceRow
```

要替换为 iOS / Wear OS 风格：实现 `PreferenceRenderers` 并通过
`CompositionLocalProvider(LocalPreferenceRenderers provides ...)` 注入即可。

---

## 5. 添加新设置项的检查清单

### 5.1 业务模块侧

1. **确定 KV 字段**：若还没有对应 KVItem，先在 `LxxxKV` 中用 `obtain<T>(key, default)` 声明
2. **编写 Provider 函数**：
   ```kotlin
   @Factory
   @Named("settings_<module>")
   fun provideXxxSettings(): SettingsGroup = settingsGroup(key = "<module>", order = X, title = { "..." }) {
       switch(<kv>.<field>, title = { "..." }, summary = { "..." })
       // ...
   }
   ```
3. **业务模块的 Koin 注解处理**：确认 build.gradle.kts 已启用 KSP（`alias(libs.plugins.ksp)` + `setupKoin()`）
4. **写测试**：参见 [testing-guide.md](testing-guide.md) 的"新增 SettingsGroup 贡献"模板
5. **重新构建**：`./gradlew :lsettings:commonTest :lplayer:commonTest`

### 5.2 添加新偏好类型

每新增一种 `Preference` 子类，必须同时完成：

1. **common**：在 `settings/Preference.kt` 定义新 sealed 子类
   - 主构造器接收 `MutableState<T>` + `writeBack`
   - 次构造器兼容测试代码的 `value: T, onValueChange: (T) -> Unit` 形式
2. **common DSL**：在 `SettingsGroupBuilder` 接口与 `SettingsGroupBuilderImpl` 实现里都加 `xxx()` 方法
3. **lsettings Renderer**：在 `PreferenceRenderers` 加 `renderXxx` 方法
4. **lsettings Row**：在 `component/preferences/XxxPreferenceRow.kt` 实现 Material3 行
5. **测试**：
   - `DefaultPreferenceRegistryTest` 加分发验证
   - `XxxPreferenceRowTest` 加交互 / disabled 验证
6. **文档**：更新本文档的"2.2 支持的偏好类型"表格

---

## 6. 已知约束 / 后续 TODO

- **可见/可用条件联动**：[Preference.visible] / [enabled] 字段已预留，
  但当前仅支持 lambda 形式（每次重组重新求值）；未来可加入 `derivedStateOf`
  优化高频求值场景
- **跨设备同步**：当前所有持久化走 `russhwolf:multiplatform-settings`（本地）；
  若未来需要云同步，应在 `LxxxKV` 层之上再包一层
- **多语言**：`title` / `summary` 是 `@Composable () -> String`，业务模块应使用
  `stringResource()` 而非硬编码字符串（本项目目前缺这一规范）
- **设置项搜索**：搜索框未实现；未来可在 `SettingsScreenContent` 顶部加搜索栏
- **sub-group / 嵌套**：当前每个 group 是扁平的；如需嵌套可扩展为 `SettingsGroup` 含 `children: List<SettingsGroup>`

---

## 7. 相关文档

- [testing-guide.md](testing-guide.md) —— 测试规范
- [performance-analysis.md](performance-analysis.md) —— 性能分析（待补充 settings 相关）
