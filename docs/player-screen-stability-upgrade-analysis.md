# PlayerScreen：analyzer 升级与切歌重组分析

日期：2026-10-09。基线：`feat/player-cover-fast-anim` / `3c1ac6db`。
本轮基于该提交创建 `codex/player-recomposition-fixes` 分支；原工作树占用的分支未被切换或覆盖。

## 升级与兼容处理

| 项目 | 原版本 | 当前版本 |
|---|---|---|
| Kotlin / Compose compiler / kotlin-test | 2.3.21 | 2.4.20 |
| Compose Stability Analyzer | 0.8.0 | 0.15.0 |
| Ktorfit compiler plugin | 2.3.3 | 2.3.5 |
| Ktorfit runtime / Gradle plugin | 2.7.2 | 2.7.2 |
| CMP | 1.11.0 | 保留 |
| KSP | 2.3.6 | 2.3.7（与 KRouter 插件统一） |
| KRouter | 0.0.8 | 0.0.9 |

[analyzer 0.15.0 发布说明](https://github.com/skydoves/compose-stability-analyzer/releases/tag/0.15.0)和[版本对应表](https://skydoves.github.io/compose-stability-analyzer/version-map/)要求 Kotlin 2.4.20。新版本修正 delegated State、跨模块 StabilityInferred、继承类型和 strong skipping 下的基线判断；因此旧报告不能直接延用。

实际遇到的兼容问题：

1. **KRouter 配置阶段 StackOverflowError**：更正初次归因：`lmedia-ui` 并没有生产依赖 `lplayer`。导出实际 Gradle configuration 后，发现 Kotlin 2.4 自动生成的 `swiftPMDependenciesForLockFilesMetadataClasspath` / `...Dependencies` 引用了其他项目；KRouter 0.0.8 遍历所有 configuration 且缺少 visited 集合，把工具元数据图也当作源码依赖递归，才发生栈溢出。已在 `~/Code/krouter` 修复上游：限定为声明的生产源码依赖桶，去重访问并延迟到项目声明完成后发现传递边；真实源码环输出完整路径，迭代遍历防止深层依赖栈溢出。新增 6 个 ProjectBuilder 回归测试和 2 个真实 Gradle TestKit 生命周期测试。0.0.9 已发布 Maven Local，LMusic 恢复原插件配置，删除临时手工 KSP 注册。
2. **Ktorfit compiler ABI**：2.3.3 registrar 对 Kotlin 编译器扩展点的转换失败。5 个模块统一使用 compiler 2.3.5，保留 runtime；[官方发布说明](https://github.com/Foso/Ktorfit/releases/tag/2.7.5)确认 2.3.5 对应 Kotlin 2.4.0+。
3. **Wasm `js()` 位置限制**：Worker URL 的 JS 表达式移入独立顶层函数，返回 `.href` 字符串再创建 Worker。源码编译已通过。
4. **Wasm 泛型 serializer 无效二进制**：完整 dump 会触发 library 链接和 Binaryen 校验，发现 `SubsonicResponseWrapper<T : SubsonicResponse>` 的生成 serializer 参数类型不匹配。这对应 [KT-89275 的官方修复](https://github.com/JetBrains/kotlin/commit/6847c003ab057a8da10ed300e8ecc525966afa3f)，修复发布在 [2.4.21-RC](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21-RC)。使用无类上界的私有 wire surrogate 生成 serializer，再显式构造有上界的公共 wrapper，保留原 JSON envelope、descriptor 名称和公共类型约束。新增成功响应、具体子类、round trip、缺失/null 响应测试。

本地 SDK 路径只写入忽略的 local.properties，不进入版本控制。

analyzer 0.15.0 的 dump/check 按任务名筛选，会错误包含 Wasm library 链接和 Binaryen 优化，同时漏掉新版 AGP 的 `compileAndroidMain`。根构建收窄这两个任务的依赖到源码编译，保留 includeTests 选项。首次全量 dump 拉起的大型优化运行数分钟后主动停止；适配后的 development/production 两份链接产物已用 Node 24.19.0 `WebAssembly.validate` 验证为有效。**这不是完整 Binaryen 优化或浏览器运行测试通过的声明。**

## 重组分析：已经证明什么

### 主题动画存在整体失效传播

2026-10-10 后续：应用全局动画已关闭，并稳定播放器回调。关闭后的状态归因与真实采样见 [后续报告](player-screen-theme-disabled-analysis.md)。本节保留此前开启动画时的分析记录。

后续对真实 MaterialKolor 的实现追踪与五项 UI 对照测试，见 [颜色动画专项分析](materialkolor-color-animation-recomposition-analysis.md)。开启动画时，读取主题和完全不读取主题的两个子组件均增加 12 次提交；关闭动画均增加 1 次，结束后不再增加。此数值来自固定 tween 的诊断场景，不代表生产切歌预算。

`LMusicTheme` 调用 MaterialKolor 4.0.4 的 `DynamicMaterialTheme(animate = true)`。依赖源码中 `animateColorScheme` 每次返回新的 ColorScheme，而 Material3 1.9.0 的 `LocalColorScheme` 定义为 **staticCompositionLocalOf**。

static local 的值改变会使 provider 的 content 整体失效，不能靠把读取移到子函数来变成仅消费者更新。[官方解释](https://developer.android.com/develop/ui/compose/compositionlocal#compositionlocal-alternatives)。

编译器报告显示：

```text
restartable skippable ... fun PlayerScreen.Content(...)
fun rememberPlayerBackgroundColor()
restartable skippable ... fun PlayerScreenContent(...)
```

返回 State 的 helper **没有独立 restart scope**。原注释声称它已隔离主题逐帧重组，不成立，现已更正。

新增 `PlayerBackgroundColorRecompositionTest`：固定时钟、8 次颜色方案替换、像素检查、外部 SideEffect 计数。宿主从 **1 次提交变为 9 次**。这是当前行为的诊断复现，**不是已达到零额外重组的性能契约**。

另外尝试过“返回 Unit 的子 scope + State bridge”，同一测试仍为 1 → 9，因此没有保留这个无效优化。原因是 static local 的 provider 失效范围仍然没有改变。

### 真实应用切歌采样

Android 测试模拟器，诊断构建 `-Plmusic.trace.player=true`，清日志后通过 media_session 自动切歌。一次采样结果：

| 作用域 | 窗口内执行事件 |
|---|---:|
| PlayerScreen.Content | 6 |
| PlayerScreenContent | 6 |
| PlayerDynamicHeader | 6 |
| DefaultBlurBackground / BlurBackground | 各 6 |
| SeekbarLayout | 6 |

上表摘录本地采样汇总；原始日志与临时 JSON 不纳入版本控制。这是 analyzer 事件数，不等同于成功提交次数，也不是发布版帧时间；多个实例按函数聚合。**不能把这一次的 6 直接当作可靠的切歌预算。**

`currentCover`、`sampledPlaybackKey`、`backgroundColor`、`onSeedColorChanged` 回调在这些执行间发生身份变化，这是后续检查跳过条件的线索。`queue: Flow` 被标记 unstable，但官方编译器仍显示 PlayerScreenContent skippable；strong skipping 下同一 Flow 实例可跳过，不能单凭 unstable 标签认定它是根因。

切歌本身会改变 currentItem、duration、封面请求及歌词；封面调色板又写入 seedColor，触发主题动画。前者包含必要更新。主题传播机制已单独复现，但本次日志尚不能把全部 6 次逐一归因，也没有证明所有重复执行都来自主题。

### 诊断日志会改变订阅的问题已修复

日志在组合内部格式化任意对象；模型的 toString 可能读取 Snapshot State，给被测 scope 意外增加订阅。现在用 `Snapshot.withoutReadObservation` 包住格式化和输出，并补充 fqName、referenceChanged、writeSite。

测试用一个 toString 读取播放位置 State 的模型复现：修复前观察到 1 次读取，修复后 0 次。这个测试证明日志的通用观察者效应；**未证明它就是这次 PlayerScreen 重组的唯一原因**。

## Agent 可直接执行的流程

```sh
# 生成诊断版；默认构建不打开 lplayer 全量 tracing
./gradlew :androidApp:assembleDebug -Plmusic.trace.player=true

# 检查成功提交和像素，不依赖肉眼 highlighter
./gradlew :lplayer:jvmTest --tests '*PlayerBackgroundColorRecompositionTest' --tests '*ScaleBlurDrawPhaseTest'

# 仅采当前应用进程与固定操作窗口；场景进入/初始化应在清日志之前完成
adb -s <test-device> logcat -c
adb -s <test-device> shell cmd media_session dispatch next
# 等待封面/歌词/主题更新完成，按场景固定等待或可观察完成条件
adb -s <test-device> logcat -d -v threadtime 'Recomposition:I' '*:S' > /tmp/player-switch.log
python3 scripts/summarize-recompositions.py /tmp/player-switch.log

# 有经重复验证的预算后再启用；超额 exit 1，无有效事件 exit 2
python3 scripts/summarize-recompositions.py /tmp/player-switch.log --max-events com.lalilu.lplayer.screen.PlayerScreen.Content=6
```

脚本按 fqName 汇总事件、参数值变化、仅身份变化、内部状态变化，处理 logcat 截断造成的无效 UTF-8。它不提供完整因果关系，不应替代确定性 UI 测试。只检查数字而没有有效事件会造成假通过，所以空日志明确失败。

编译器报告在 `lplayer/build/compose-reports`，metrics 在 `lplayer/build/compose-metrics`。analyzer 基线通过 `./gradlew stabilityDump` 生成，用 `./gradlew stabilityCheck` 比对；静态基线限制稳定性变化，UI 测试另行限制实际更新范围。

## 下一步优化方向

后续已关闭全局主题颜色动画，并保留播放器背景的绘制阶段局部动画，见 [后续报告](player-screen-theme-disabled-analysis.md)。新增需要动画的元素时，应分别验证 drawBehind、graphicsLayer 等局部动画方案，避免把每个动画帧发布成全局 MaterialTheme。

随后为 PlayerScreenContent 注入固定队列、封面加载及歌词数据，分别约束：播放进度只更新 seekbar/歌词、同一封面快路径不重复刷新、调色板完成后无关 Header 不逐帧组合。生产 Koin/媒体源的真实 E2E 与纯 UI 契约测试应同时保留。

## 最终验证结果

- 针对性 JVM 测试 **12 项全部通过**：PlayerBackgroundColor 1、ScaleBlur 2、Highlighter 2、DebugRecomposeLogger 1、KoinModules 4、SubsonicResponseWrapper 2。
- Android Debug APK 打包成功，并在测试模拟器完成诊断版启动和自动切歌采样。
- iOS Arm64 / SimulatorArm64、JVM、Wasm 源码编译通过；iOS 未进行设备运行或 framework 链接测试。
- Wasm development/production library 已链接，Node 的 `WebAssembly.validate` 均为 true；完整 Binaryen 优化主动停止，浏览器运行未验证。
- `stabilityDump` 和 `stabilityCheck` 均成功，composeApp、lplayer、llyricview 三份新基线已保存。收窄后的检查未再启动 Wasm library 链接/优化；缓存源码下 dump/check 各约 8 秒。
- 日志脚本验证了参数变化、身份变化、状态变化和预算退出码；`git diff --check` 通过。
- 测试模拟器尺寸/密度覆盖已恢复并关闭；上述诊断验证使用模拟器。

剩余兼容性提示包括既有 Coil/CMP Skiko 版本对齐警告、KSP configuration 弃用与 Gradle 10 弃用警告；本次没有把源码编译通过外推成所有平台运行时兼容性已证明。

## 测试归属修正（2026-10-10）

`lplayer.jvmTest → lmedia-ui` 虽非这次栈溢出的根因，仍是不必要的测试依赖。原 SourceRecoveryUiTest 混合了 AudioItemCard、SongCard 和提示层测试；3 项 AudioItemCard 测试现已迁到 `lmedia-ui.jvmTest`，播放器保留 SongCard 和提示层的 3 项测试，移除 lplayer 对 lmedia-ui 的测试依赖。以后跨模块业务链路测试应放 composeApp 或独立 integration-tests 模块；确实被双向使用的底层组件/接口再抽到低层模块，不为单个放错位置的测试拆生产模块。

迁移验证：两个模块 SourceRecoveryUiTest 各 3 项，合计 6 项全部通过。排除上述 SwiftPM 元数据 configuration 后导出的项目依赖图无环，且 lplayer 已无 lmedia-ui 测试依赖。该核实替代了初次报告中未经验证的反向生产依赖判断。

## KRouter 0.0.9 上游修复与本地接入（2026-10-10）

KRouter 插件 8 项、compiler 16 项、跨模块 test-app 12 项测试通过；全部平台 Maven Local 发布成功。真实应用重建生成 JVM 25 个路由 / 17 个 ModuleProvide，Android 18 个 ModuleProvide。升级 KSP 后清理旧 build/kspCaches；无需关闭增量模式或日志。单独执行 clean 时，现有 VLC 插件的任务对象不能被配置缓存序列化，应对 clean 使用 --no-configuration-cache；正常构建继续开启配置缓存。

完整 Maven Local 接入命令成功（1116 个任务，3 分 51 秒）：18 项针对性测试、Android Debug APK、iOS Arm64 / SimulatorArm64、Wasm 源码编译和 3 个 stabilityCheck 均通过，配置缓存正常存储。

再次构建成功；最终相同输入运行明确显示 `Reusing configuration cache`，2 秒完成（1022 个任务 up-to-date），3 个稳定性检查再次通过。上游提交：`eaea5e419d660d405bf4f15b5f2a37aeb660c079`，已推送 central。

[上游 CI](https://github.com/cy745/KRouter/actions/runs/37970050066) 成功；[0.0.9 Release](https://github.com/cy745/KRouter/releases/tag/0.0.9) 已发布，自动触发 Maven Central 发布工作流。

[Maven Central 自动发布工作流](https://github.com/cy745/KRouter/actions/runs/37970538096) 已成功，所有签名和 publishAndRelease 任务完成。随后直接访问 repo.maven.apache.org，core、compiler、ksp-collector、plugin POM/JAR 和 Gradle plugin marker 0.0.9 全部 HTTP 200。下载的插件 JAR 与 Maven Local 真实应用验证所用 JAR SHA-256 完全一致（`2a33c912b2aa4b1bf562520691f31d7253d69b168aad41a3ca714877df74e68b`）。发布与公开下载验证完成。
