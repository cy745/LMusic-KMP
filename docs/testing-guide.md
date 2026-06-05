# 测试指南

本项目的测试代码与生产代码一同入库，要求每个新功能/重构都附带测试。
本指南基于项目当前的测试基础设施，介绍分层、规范与模板。

---

## 1. 测试分层

| 层级 | 工具 | 适用 | 入口 |
|---|---|---|---|
| 纯逻辑 | `kotlin.test` | 无 Compose 依赖的纯函数/数据类 | `commonTest` |
| 协程 | `runTest` + `kotlinx-coroutines-test` | 含 Flow/StateFlow 的逻辑 | `commonTest` |
| Flow | `turbine` | 验证 `KVItem.flow()` 等流行为 | `commonTest` |
| Compose UI | `runComposeUiTest` + `compose.ui.test` | Composable 渲染、交互、tag 查询 | `commonTest`（JVM） |
| Koin 集成 | `startKoin { ... }` + `stopKoin()` | 验证 Koin 装载后的收集行为 | `commonTest` |

> 当前 `build-logic` 的 `setupMultiplatform()` 把 `wasmJs.testTask` 关闭，
> 所以测试目标限定为 `commonTest` (JVM) + 各平台 instrumented test。

---

## 2. 测试工具一览

放在 `common` 模块的 `src/commonMain/kotlin/com/lalilu/common/` 下，
所有 KMP target 共享：

| 类 / 扩展                       | 路径                                     | 作用                                       |
|--------------------------------|------------------------------------------|------------------------------------------|
| `InMemoryKVSaver`              | `kv/testing/InMemoryKVSaver.kt`           | 纯内存 `KVSaver`，模拟持久化                   |
| `TestKVContext`                | `kv/testing/TestKVContext.kt`             | 跳过 Koin 注册的 `KVContext` 基类             |
| `testKoin { ... }`             | `testing/TestKoin.kt`                     | 创建并自动 close 一个临时 Koin 容器            |
| `FakeToaster`                  | `testing/FakeToaster.kt`                  | 记录 `info/warn/error` 的假 Toaster           |

> 之所以放在 `commonMain`：上述实现都无平台 / Compose 依赖，
> 放在 main 源码集既能被多模块复用，又能避免 KMP testFixtures 的样板。

---

## 3. 约定

1. **测试类名**：`<被测类>Test`，与被测类放同包路径
2. **测试方法名**：用反引号括起来的描述性句子，如 `` `group has correct key and order` ``
3. **断言**：仅使用 `kotlin.test.*`，不直接 import JUnit / AssertJ
4. **协程**：用 `runTest { ... }`，不在测试中调用 `runBlocking`
5. **Compose**：用 `runComposeUiTest { setContent { ... } }`
6. **tag**：所有可测试 Composable 必须带 `Modifier.testTag("...")`，
   命名约定 `preference_<type>_<key>` 与 `settings_group_*`
7. **依赖**：新增 `commonTest.dependencies { ... }` 项时同步更新本指南

---

## 4. 必测场景模板

### 4.1 新增 `Preference` 子类

每新增一种偏好类型，必须包含：

```
1. 构造测试：默认值 / 初始值正确
2. 行为测试：onValueChange 写入并通过 state 读回最新值
3. 渲染分发测试：DefaultPreferenceRegistryTest 增加对应分发断言
4. 至少 1 个 *Row 测试 (Click / Enable / Visible)
5. 文档：本指南更新映射表 + settings-guide.md 的 2.2 表格
```

### 4.2 新增 `SettingsGroup` 贡献

每个业务模块贡献一个 `SettingsGroup` 时，必须包含至少 3 个测试：

```kotlin
class XxxSettingsTest {
    @Test fun `group has correct key and order`() { ... }
    @Test fun `group contains all expected preferences`() { ... }
    @Test fun `preference writes through KVItem`() { ... }
    // 可选：
    @Test fun `click preference side effect works`() { ... }
    @Test fun `preference visible or enabled lambda is respected`() { ... }
}
```

参考 `lplayer/src/commonTest/.../LPlayerSettingsTest.kt`。

> **注意**：`LPlayerKV` 是 `object`，首次访问会触发 Koin 全局初始化（`KVConverter.findConverter`）。
> 测试时要么提前 `startKoin { modules(module { single { Json { ... } } })`，
> 要么在 `setup { ... }` 中用 `KVContext.registerSaver(saver)` 隔离。

### 4.3 新增 Composable Row

```kotlin
class XxxPreferenceRowTest {
    @Test fun `clicking the row invokes onValueChange`() = runComposeUiTest { ... }
    @Test fun `disabled row does not respond`() = runComposeUiTest { ... }
    // 可选：
    @Test fun `summary is rendered when provided`() = runComposeUiTest { ... }
    @Test fun `icon is rendered when provided`() = runComposeUiTest { ... }
}
```

### 4.4 新增 Dialog

```kotlin
class XxxDialogTest {
    @Test fun `dialog renders with correct tags`() = runComposeUiTest { ... }
    @Test fun `confirm button triggers onConfirm with current value`() = runComposeUiTest { ... }
    @Test fun `dismiss button triggers onDismiss`() = runComposeUiTest { ... }
}
```

---

## 5. 运行测试

```bash
# 单模块
./gradlew :common:commonTest
./gradlew :lsettings:commonTest
./gradlew :lplayer:commonTest

# 全部（按现有 CI 习惯）
./gradlew commonTest

# 仅 JVM
./gradlew :common:jvmTest
```

---

## 6. 覆盖率目标

| 模块                 | 目标      |
|--------------------|---------|
| common (settings + kv) | ≥ 90% |
| lsettings (UI + Dialog) | ≥ 70% |
| 业务模块 Settings 贡献 | 关键路径全覆盖，无强制数值 |

---

## 7. 反模式

| 反模式                                              | 正确做法                                  |
|---------------------------------------------------|--------------------------------------|
| 直接 `KoinPlatform.getKoin()`                       | 用 `testKoin { ... }` 或 `startKoin { modules(...) }` |
| 在 `runBlocking` 中跑协程逻辑                        | 用 `runTest { ... }`                   |
| 在 UI 测试中用真 Toaster                            | 注入 `FakeToaster` 或 `NoOpToaster`   |
| `Thread.sleep(...)` 等待异步                       | 用 `turbine.test { awaitItem() }`      |
| 跳过断言仅"打印结果"                                  | 每个 `runTest` / `runComposeUiTest` 至少 1 个断言 |
| 期望 `KoinApplication has not been started` 错误 | 在 `@BeforeTest` 中 `startKoin { ... }` 并 `@AfterTest` 中 `stopKoin()` |

---

## 8. 平台差异

测试统一在 `commonTest` 跑。涉及 `expect/actual` 时，测试代码本身只能放 `commonTest`；
具体平台的行为验证交由该平台的 instrumented test。
