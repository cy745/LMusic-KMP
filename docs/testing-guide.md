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

各模块 `commonTest` 自带的依赖（新增依赖时同步更新本表）：

| 模块 | commonTest 依赖 |
|---|---|
| `lmedia:lmedia-client` | `kotlin.test`、`kotlinx-coroutines-test`（`runTest`）、`ktor-client-mock`（HTTP 层用例） |

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

### 5.1 前置条件

- **JDK 21**：`vlc-setup` 插件要求 JVM ≥ 21，JDK 17 会在配置阶段直接失败。本机有多个 JDK 时用
  `JAVA_HOME` 指定，例如 `JAVA_HOME=~/.jdks/corretto-21.0.11`。
- **VLC 原生资源**：桌面端原生播放用例依赖仓库内的 VLC 二进制
  （`lplayer/src/jvmMain/assets/<os>/vlc/`，被 `.gitignore` 排除，不随克隆分发）。
  首次运行或资源过期（改了 `composeApp` 的 `vlcSetup` 配置）时先执行：

  ```bash
  ./gradlew :composeApp:vlcSetup
  ```

  ⚠️ `vlcSetup` 会**先删光目标目录中的 dll 再重写**，不要往该目录放自备 dll。
  用 `shouldIncludeAllVlcFiles = true` 时资源约 58 MB（UPX 压缩后），minimal 配置只有约 10 MB——
  后者会让网络音源与内存回调播放失败。
- **真实音频夹具**：`LMUSIC_NATIVE_AUDIO_FIXTURE` 需指向一个 **≥ 12 秒**的真实音频文件
  （原生用例会 seek 到 12s，过短会失败）。可用 ffmpeg 生成：

  ```bash
  ffmpeg -f lavfi -i "sine=frequency=440:duration=30" -ac 1 -ar 44100 -b:a 64k native-audio.mp3
  ```

### 5.2 启用原生用例

`lplayer` 的原生播放用例默认**跳过**（`assumeTrue` 环境守卫），需要同时指定两个环境变量：

```bash
export LMUSIC_NATIVE_RESOURCES="$PWD/lplayer/src/jvmMain/assets/windows"   # 含 vlc/ 的目录
export LMUSIC_NATIVE_AUDIO_FIXTURE="$PWD/build/native-fixtures/native-audio.mp3"
./gradlew :lplayer:jvmTest
```

未设置时这些用例计为 `skipped` 而非失败。注意：设置后 `jvmTest` 不再命中 up-to-date 缓存，会真实重跑。

⚠️ **Gradle daemon 不继承新设的环境变量**：Test 任务取的是 daemon 的环境，所以设完
`LMUSIC_*` 之后要先 `./gradlew --stop`，否则用例会静默全部跳过（看测试 XML 里的
`tests=N skipped=N` 才是真相，控制台的 `BUILD SUCCESSFUL` 什么也证明不了）。

### 5.2.1 WebDAV 真实服务用例（`lmedia:lmedia-client`）

`WebDavLiveServerTest`（`src/jvmTest`）对着真实 WebDAV 服务跑全链路，由环境变量守卫：

```bash
docker run -d --name lmusic-webdav -p 8080:80 -e AUTH_TYPE=Basic \
  -e USERNAME=lmusic -e PASSWORD=lmusic-test \
  -v "<测试库目录>:/var/lib/dav/data" bytemark/webdav

export LMUSIC_WEBDAV_URL=http://127.0.0.1:8080
export LMUSIC_WEBDAV_USERNAME=lmusic
export LMUSIC_WEBDAV_PASSWORD=lmusic-test
export LMUSIC_WEBDAV_ROOT=/
./gradlew --stop && ./gradlew :lmedia:lmedia-client:jvmTest
```

### 5.3 设备测试（Android instrumented）

`lplayer` 有一组跑在真实 Media3 上的用例，位于 `lplayer/src/androidDeviceTest/`：

```bash
./gradlew :lplayer:connectedAndroidDeviceTest
```

> ⚠️ **该 Gradle 任务在部分环境里发现不到任何用例**：会打印 `Starting 0 tests on <AVD>`，
> 用例一个都不执行（测试 APK 本身能正常生成，包名 `com.lalilu.lplayer.core.test`）。
> 原因尚未定位，**不要把它当成"通过"**。

绕过方式（手动安装 + 指定类运行，实测可用）：

```bash
# 1. 先产出测试 APK
./gradlew :lplayer:assembleAndroidDeviceTest

# 2. 安装（-t 允许安装 testOnly 包）
adb install -r -t lplayer/build/outputs/apk/androidTest/lplayer-androidTest.apk

# 3. 指定类运行；一次传多个类（逗号分隔）可能报 failed to attach，建议逐个跑
adb shell am instrument -w -e class com.lalilu.lplayer.extensions.QueueControlPlayerDeviceTest \
  com.lalilu.lplayer.core.test/androidx.test.runner.AndroidJUnitRunner
```

这些用例是**自 instrumenting** 的：manifest 里 `targetPackage` 指向测试包自身，因此不需要先装 App。

`lmedia-client` 也有一组设备用例（`lmedia/src/androidDeviceTest/`，包名
`com.lalilu.lmedia.client.test`），跑的是 WebDAV 全链路：扫描 → 回环代理播放 → taglib JNI 提取。
它需要**宿主上起一个 WebDAV 服务**，并且模拟器能通过 `10.0.2.2` 访问宿主：

```bash
./gradlew :lmedia:lmedia-client:assembleAndroidDeviceTest
adb install -r lmedia/lmedia-client/build/outputs/apk/androidTest/lmedia-client-androidTest.apk
adb shell am instrument -w -e class com.lalilu.lmedia.source.webdav.WebDavSourceDeviceTest \
  com.lalilu.lmedia.client.test/androidx.test.runner.AndroidJUnitRunner
```

⚠️ 测试 APK 是**独立安装**的，必须自带 `usesCleartextTraffic="true"`
（`src/androidDeviceTest/AndroidManifest.xml`）——应用清单里的那份管不到它，否则扫描会以
`CLEARTEXT communication ... not permitted by network security policy` 失败。
服务不可达时用例会跳过，跳过原因（host:port + 异常）会带在 `assumption failed` 日志里。

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

### 8.1 平台专属用例的跳过约定

桌面端同一份 `jvmTest` 会在 Windows / macOS / Linux 上执行，而部分被测代码本身是平台专属的。
这类用例必须在**用例体第一行**显式声明所需平台，不匹配时**跳过而不是失败**：

```kotlin
@Test
fun onlyMeaningfulOnMac() {
    assumeDesktopOs(DesktopOs.MACOS)   // Windows / Linux 上计为 skipped
    ...
}
```

`assumeDesktopOs` 与 `DesktopOs` 定义在 `lplayer/src/jvmTest/kotlin/com/lalilu/test/DesktopOsAssumptions.kt`，
底层是 JUnit4 的 `Assume.assumeTrue`（本模块的测试运行在 JUnit4 runner 上），
Gradle 会正确计成 `skipped` 并带上原因。反向同理：只在 Windows 上成立的用例写
`assumeDesktopOs(DesktopOs.WINDOWS)`。

两点注意：

- 若类里用 `init { ... }` 初始化平台专属资源，必须改成 `lazy`：`init` 在**构造阶段**执行，
  会早于守卫，导致非目标平台仍然报错（`RococoaTest` 就是这个情况）。
- **不要用它掩盖真实缺陷**：环境缺失（原生库未就绪、夹具未提供）用环境守卫处理；
  跨平台的路径分隔符、编码、大小写等问题属于 bug，必须修而不能跳过。

