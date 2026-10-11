# CMP 重组排查与自动化约束调研

调研日期：2026-10-09。代码基线：`feat/player-cover-fast-anim`，提交 `3c1ac6db`。
本文前期调研基于该提交；随后升级与实测见 [PlayerScreen 升级分析](player-screen-stability-upgrade-analysis.md)。下述未明确标记完成的能力仍是建议。

## 结论

建议采用三层证据：**编译器报告解释跳过条件；运行时事件解释触发原因；确定性 UI 测试约束更新范围**。真正的性能收益再用平台基准测试验证。

Agent 最需要的是可重复场景、可读事件、失败断言和基线差异。让 Agent 观看 highlighter 颜色或反复点击手机，无法稳定区分状态变化、动画推进、重新进入组合与无效重组。

优先扩展现有 `ScaleBlurDrawPhaseTest` 的思路，而不是重新引入一套视觉检测工具。重组预算应描述具体操作的契约，不是给整棵 UI 树设置一个固定总次数。

## 基线分支已有能力（升级前）

- CMP `1.11.0`、Kotlin `2.3.21`、stability analyzer `0.8.0`，不是初始工作树中的 analyzer `0.6.0`。
- `lplayer/build.gradle.kts` 已应用 analyzer，`PlayerScreen`、`PlayerScreenContent`、`SeekbarLayout` 已标记 `@TraceRecomposition(traceStates = true)`。
- `DebugRecomposeLogger` 能输出组件名、tag、耗时、参数和内部状态变化；目前是多行文本，未输出 `referenceChanged`。
- 原分支把主题读取抽为 `rememberPlayerBackgroundColor()`，但后续编译器报告与 UI 测试证实它不能隔离主题动画：返回 State 的函数没有独立 restart scope，MaterialTheme 的 static CompositionLocal 更会使 provider 下的内容整体失效。
- `DefaultBlurBackground` 将模糊半径、压黑、遮罩动画的读取放在绘制/图层期；`PlayerDynamicHeader` 将封面位移和缩放放在 `graphicsLayer`。
- `ScaleBlurDrawPhaseTest.drawPhaseRadiusRepaintsWithoutRecomposingHost()` 同时检查像素改变与宿主组合次数不变，已实现一个很有价值的局部性能契约。
- `RecomposeHighlighterTest` 验证边框及计数重置。这证明诊断工具的行为，不证明播放器整体没有重组回归。
- 当前源码检索未发现完整 `DefaultBlurBackground` / `PlayerScreenContent` 的重组契约测试，也未发现官方 `reportsDestination` / `metricsDestination` 配置。
- `lplayer` 的 Compose UI 测试依赖目前在 `jvmTest`；迁移到 `commonTest` 还需要配置其他目标。Wasm 测试任务在 build-logic 中被关闭。

以上是源码事实，不是性能实测结论。

## 方法选择

| 方法 | 适合 Agent 获取的证据 | 局限与建议 |
|---|---|---|
| 官方 Compose compiler reports/metrics | restartable/skippable、参数与类型稳定性、修改前后差异 | 不记录某次操作实际发生了多少重组；作为静态诊断层 |
| 已有 stability analyzer | 参数变化、内部状态变化、身份变化、组合事件 | 优先复用；日志探针有开销，事件次数不能直接视作帧数或成功提交次数 |
| 业务作用域内的 `SideEffect` 计数 | 成功应用的组合执行次数、操作前后增量 | 最适合做确定性的更新范围断言；要准确放置探针 |
| 自定义计数 semantics | 测试通过节点读取计数 | semantics 本身可能引入状态读取或失效，优先使用外部记录器 |
| `Recomposer.changeCount` / composition observer | 整体变化、失效作用域、底层组合事件 | 前者不等于组件重组总数；后者属于实验性 tooling API，维护成本较高 |
| Rebugger | 手动指定参数的变化日志 | 可用于小范围临时诊断；不是完整因果追踪，不必与现有 analyzer 重复接入 |
| ComposeInvestigator | 编译器插桩追踪重组原因 | 需要另行验证 Kotlin/CMP/目标支持；本项目已有同类能力，优先级低 |
| Android Macrobenchmark + composition tracing + Perfetto SQL | 帧耗时、卡顿、组件执行片段耗时 | 性能验收层；Android 方案不能直接代表 Desktop/iOS |
| Layout Inspector / highlighter | 人工确认范围、快速发现异常 | 保留为辅助观察工具，不作为 CI 计数来源 |
| 截图或视觉 Agent E2E | 用户操作、最终画面、真实应用整合 | 图像不能揭示没有视觉变化的重复组合；需附加运行时事件才能检查重组 |

来源：[编译器 DSL](https://kotlinlang.org/docs/compose-compiler-options.html)、[编译器选项 API](https://kotlinlang.org/api/kotlin-gradle-plugin/compose-compiler-gradle-plugin/org.jetbrains.kotlin.compose.compiler.gradle/-compose-compiler-gradle-plugin-extension/)、[analyzer KMP 文档](https://skydoves.github.io/compose-stability-analyzer/gradle-plugin/kotlin-multiplatform/)、[runtime tooling API](https://developer.android.com/reference/kotlin/androidx/compose/runtime/tooling/package-summary)、[Rebugger](https://github.com/theapache64/rebugger)、[ComposeInvestigator](https://github.com/jisungbin/ComposeInvestigator)。第三方文档的 main 分支可能比本项目版本新，功能需按版本核对。

## 适合 Agent 的排查流程

1. 固定提交、目标平台、编译选项、窗口尺寸、数据集与操作。先写失败场景，再修改实现。
2. 对一次操作建立边界：初始组合和加载稳定后截取 baseline，执行操作，再统计这个窗口里的增量。
3. 输出每个业务作用域/实例的事件：`scenario`、`step`、`scope`、`instanceKey`、参数变化、引用变化、状态变化。将图片/队列记录为 key 或长度，避免打印整个对象。
4. 用编译器报告和源码定位原因：composition 中提前读取了高频 State？父级参数变了？新建了 equals 相同但身份不同的对象？CompositionLocal 更新？组件重新挂载？
5. 一次修复一个原因。重跑契约测试，并确认文本、画面、交互仍更新。
6. 对用户可感知的卡顿做平台基准测试。最终给出前后数据，而不是只报告“变成 skippable”。

建议每次测试失败输出类似以下的自定义报告（不是现有插件自带格式）：

```text
scenario=drag-cover; step=frame-42; instance=cover:A
scope=DefaultBlurBackground/content; committedDelta=1; allowed=0
param=blurProgress; referenceChanged=false
stateRead=composition; input=dragPosition
```

现有 logger 可以加一个收集事件的实现，测试时替换、结束时恢复，按 scenario 分窗导出 JSONL。全局 logger 的测试需串行或隔离，避免不同测试互相收集事件。优先使用 `RecompositionEvent` 对象字段，而不是解析带树形字符的文本。

analyzer 0.8.0 已新增 `ParameterChange.referenceChanged`，并保留 Kotlin 2.3.21。它能帮助区分真实值变化与新分配的等值对象。项目 logger 当前只看 `changed`，会丢失这一诊断维度。更新版插件可能使用不同 Kotlin 编译器版本，不建议仅为此次任务盲目升级。[0.8.0 changelog](https://github.com/skydoves/compose-stability-analyzer/blob/main/CHANGELOG.md)

## 怎样通过 UI 测试约束重组

### 1. 无关状态隔离

例如推进播放时间，允许进度/时间文本/歌词的相关作用域更新，封面加载任务和播放队列的无关条目不应跟随更新。必须在真实业务组件内部计数，只在 `setContent` 根 lambda 计数无法发现可独立重组的子作用域。

### 2. 绘制和图层期契约

拖动、模糊、透明度、位移、缩放的视觉值变化时，要求被保护的业务内容作用域 `delta == 0`，同时检查像素、位置或图层视觉结果确实变化。

现有 `ScaleBlurDrawPhaseTest` 可作为模板。但它只保护 Modifier 的宿主，不覆盖 `DefaultBlurBackground` 内部的 `BoxWithConstraints` 内容或 AnimatedContent 槽位，更不覆盖整个播放页。

### 3. 更新范围而非全局固定次数

点击某个开关后，目标组件必须展示新值，无关 sibling 的 `delta == 0`。目标组件可以使用经过确认的小范围预算。尽量避免跨平台要求“整个页面恰好重组一次”：布局、动画槽位和框架版本会改变合法执行次数。

### 4. 列表逐项约束

用业务 key 区分实例，更新一个条目后验证已挂载且未改变的其他条目不重组。固定 viewport 与数据，关闭或控制滚动动画，不能把进入可视区域、预取或复用产生的首次组合判为回归。另测重排后实例身份/remember 状态是否保留。

### 5. 动画中段和边界分开

动画启动、换图、加载相位切换、旧图退出可以合法发生组合。先使动画进入中段，再连续推进若干帧，约束业务内容不逐帧重组。用 UI/像素断言检查动画确实推进，并检测结束后是否继续产生无意义更新。

### 6. 静默与副作用约束

数据稳定后推进固定帧数，检查业务计数不再持续增长。同时统计 image request、palette extraction、Flow 订阅、effect 启动/取消次数：重复副作用并不一定能从重组总数里看出来。

### 7. 参数身份与相等性场景

分别测试：同一对象重复传入、等值新实例、真正变化的新值、普通集合与不可变 UI 模型。强跳过下不稳定参数通常比较 `===`，稳定参数比较 `equals`。不要将“参数 unstable”直接等同于“每次必重组”。Kotlin 2.0.20 起强跳过默认启用，本分支无需将启用它作为首要优化。[Strong skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping)

### 探针的正确位置和计数含义

在所要保护的作用域体内直接放 `SideEffect { sink.record(key) }`；`sink` 使用普通非 Snapshot 记录，不把计数做成 `mutableStateOf` 再在被测 UI 里读取。计数包括首次组合，应取稳定后的前后增量。需要识别重新挂载时配合 `DisposableEffect` 的进入/退出事件及实例 key。

不要封装成一个参数不变、可被跳过的 `@Composable fun Count(key, sink)` 再以它的执行数代表父级执行数；这个小函数可能被独立跳过。不要用包裹组件代表其内部所有子作用域。若需要通用探针，验证它在父级重组和子级独立重组时的语义。

函数体内 `count++` 统计执行尝试，包括可能被放弃的组合；`SideEffect` 在成功组合后运行，适合契约测试。两者口径不能混用，也不等于 measure/draw 次数。analyzer 的入口/耗时统计也应先通过小型校准测试确认口径。[SideEffect 文档](https://developer.android.com/develop/ui/compose/side-effects)

以现有测试为基础，可以将下面的方式用于单个受保护的宿主（省略 imports；仅为设计示例，未执行此片段）：

```kotlin
val progress = mutableFloatStateOf(0f)
var committed = 0
mainClock.autoAdvance = false
setContent {
    SideEffect { committed++ } // 直接位于被测作用域中
    Box(
        Modifier.testTag("target").size(100.dp)
            .scaleBlur(scale = 0.4f, radius = { (progress.floatValue * 20f).dp })
            .drawBehind { /* 绘制固定的可观察图案 */ }
    )
}
mainClock.advanceTimeByFrame()
waitForIdle()
val before = runOnIdle { committed }
repeat(30) { frame ->
    runOnUiThread { progress.floatValue = (frame + 1) / 30f }
    mainClock.advanceTimeByFrame()
    waitForIdle()
}
assertEquals(before, runOnIdle { committed })
// 还必须断言图像实际改变；完整范例见 ScaleBlurDrawPhaseTest。
```

### 时间与异步工作的约束

本分支新测试使用 `androidx.compose.ui.test.v2.runComposeUiTest`。CMP 1.11.0 已推出 v2，非 Android 目标默认 `StandardTestDispatcher`；已有旧 API 测试应在迁移时确认调度语义，不只机械替换 import。[CMP 1.11.0](https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/)

- `mainClock.autoAdvance = false`，显式推进帧和时间；状态修改通过 UI 线程操作，计数读取通过同步后的测试操作。
- `mainClock` 不是所有工作的总时钟：真实 IO、原生播放器、图片解码和 Android measure/draw 不会全被虚拟时间接管。
- `DefaultBlurBackground.loadImage()` 有真实 `Dispatchers.io`、Coil singleton、150ms 快路径与 10s 超时。直接推进 mainClock 不足以控制这些分支；应注入可控加载器/dispatcher，明确管理协程 scheduler 与 Compose clock。
- 稳定阶段测试可以等待 idle；动画中段测试不能默认等到所有动画结束才取样。无限动画要给出有界观测窗口。
- Android 文档中的新 `runWithoutImplicitWait` 有自己的版本下限，不能直接当作 CMP 1.11.0 已支持的优化。
- 图像测试固定尺寸、密度、主题与测试图片，检查关键区域或差异容差；不要用全屏截图精确像素相等来代替重组断言。

来源：[CMP UI 测试](https://kotlinlang.org/docs/multiplatform/compose-test.html)、[测试同步](https://developer.android.com/develop/ui/compose/testing/synchronization)。

## 本分支建议先保护的场景

| 输入/操作 | 应检查的契约 | 观测点 |
|---|---|---|
| 连续推进播放时间 | 无关封面内容不逐帧重组，进度/歌词仍正确更新 | PlayerScreenContent、PlayerDynamicHeader、封面内容槽位 |
| 连续改变展开进度 | 模糊/压黑/位移/缩放确实变化；固定约束下封面业务内容不逐帧重组 | graphicsLayer、scaleBlur、DefaultBlurBackground 内容 |
| 调色板返回后主题动画 | 目标是只更新背景/主题消费者；当前静态 CompositionLocal 会扩散，需先改变主题动画边界 | rememberPlayerBackgroundColor 与调用树 |
| 快速封面加载（小于 150ms） | 不进入慢加载遮罩；换图正确，动画中段不逐帧重组 | imageState、loadingState、内容实例 |
| 慢加载（越过 150ms） | 旧图保留、遮罩归属旧图；相位转换允许更新，中段有界 | loadingState、veilOwner、请求计数、像素 |
| A→B→C 连续切歌 | 过期任务取消且不覆盖 C；旧槽位按转场清理；请求数符合场景 | loader/effect 事件、imageState、mount/unmount |
| 加载失败/10s 超时 | 降级到占位、结束遮罩，随后静默 | errorState、占位视觉、持续事件 |
| 修改队列一项/重排 | 已挂载无关行不跟随更新；业务身份保留 | 按 queue item key 的行探针 |

这些是建议契约，尚未测定每个层级的合法预算。尤其 `BoxWithConstraints` 约束变化、AnimatedContent 进入/退出和 theme consumer 更新不能一律判为异常。

## 优化决策

根据失败证据选择措施：

1. 高频输入在 composition 中被读取：保持 State/lambda，推迟到真正消费处；适合时使用 `offset {}`、`graphicsLayer {}`、`drawBehind` / `drawWithContent`。
2. 主题/CompositionLocal 失效扩散：先区分 dynamic 和 static local；当前分支的返回 State helper 不能隔离 static ColorScheme 的逐帧替换。主题只发布最终值，局部绘制期动画是优先验证方向。
3. 等值新对象导致强跳过失效：检查数据发布与模型身份，按实际语义引入不可变 UI 模型或稳定契约。不要给任意 List 或可变对象盲目加 `@Stable` / stability config。
4. expensive work 重复：用正确的 remember keys 或移到 UI 外，避免在 composition 中做排序、请求、解码。
5. 状态变化频率远高于 UI 需要：例如滚动位置变成“是否显示按钮”，用 `derivedStateOf`；需要每帧变化的动画浮点输入不会因为包了它就自动降低重组。
6. 列表身份丢失：使用稳定唯一 key，并为不同项类型合理配置 contentType；后者解决复用，不是跳过保证。
7. UI 不更新：检查原地修改、缺少可观察 State、错误 remember key 或错误稳定性承诺，不能以跳过次数增加作为成功。

官方依据：[Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)。

## 性能验证与 CI

建议普通 PR 跑 JVM 确定性重组契约，按受影响模块筛选测试；关键变更补 Android/iOS 代表场景；定期在固定真机跑 Android Macrobenchmark。JVM 测试验证共享逻辑的隔离性质，不能证明 Android/iOS 渲染速度。

Android 使用 `FrameTimingMetric` 等帧指标，以及 composition tracing / `TraceSectionMetric` 定位热点。Agent 可通过 Perfetto Trace Processor SQL 导出指定进程、线程、操作窗口内 slice 的数量与耗时。查询要先确定 trace 中实际名称；嵌套片段时间不能直接相加视作总 CPU 时间。不要把 runtime logger 的 wall time 当成 GPU/整帧耗时。

Desktop 可用固定操作加 JVM profiler/JFR 分析 CPU 与分配，iOS 可用 Instruments；各平台渲染与线程模式不同，应分别验收。关闭 highlighter 和调试插桩后再做性能对比，保留版本、设备、刷新率、缓存状态、编译模式和原始产物。

本项目 highlighter 的 `ModifierNodeElement.equals()` 恒为 false，会主动触发节点更新，同时绘制标签、维护重置任务。因此它是有扰动的诊断探针，不宜拿开启它时的性能作为生产表现，也不能将它的节点 update 数当成所有子组件的重组数。

静态层使用官方 reports/metrics，先建立关键模块的基线与差异报告，再考虑阻断规则。强跳过下不应因为某类型 unstable 就让整个 CI 失败。编译器升级时重新评估基线，不能混用 debug/release 或不同目标的报告。

```kotlin
// 建议在需要分析的模块启用，当前尚未添加。
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}
```

参考：[composition tracing](https://developer.android.com/develop/ui/compose/tooling/tracing)、[Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)、[Perfetto SQL](https://perfetto.dev/docs/analysis/perfetto-sql-getting-started)。

## 推荐实施顺序

1. 保留现有工具，扩展局部 draw-phase 测试为多帧测试，并采用明确的成功组合计数口径。
2. 给真实播放器/封面内容增加可观测点；用 Fake 图片加载与固定数据补快路径、慢路径、取消、主题动画测试。
3. 对现有 logger 增加结构化事件收集与 `referenceChanged` 信息，失败时输出 scope/key/操作窗口差异。
4. 添加关键模块官方 compiler reports/metrics，让 Agent 在失败后获取静态证据。
5. 接 Android 真机帧性能基准，验证这些结构契约实际带来的收益。

此次不改播放器实现或依赖，不声称已解决任何新的卡顿。

## 本次复核

已定向运行以下现有测试：`ScaleBlurDrawPhaseTest` 2 项、`RecomposeHighlighterTest` 2 项，共 4 项，全部通过，构建耗时 1m23s。XML 报告确认 failures/errors 均为 0；这只证明两个局部契约，不代表完整播放器性能验收。

初次调研构建存在既有 Skiko 依赖版本不一致和旧 UI 测试 API 弃用提示，该阶段未修改依赖；`git diff --check` 通过。后续升级与修复见 [升级报告](player-screen-stability-upgrade-analysis.md)和 [修复报告](player-screen-theme-disabled-analysis.md)。

```shell
./gradlew :lplayer:jvmTest --tests '*ScaleBlurDrawPhaseTest' --tests '*RecomposeHighlighterTest' --console=plain
```
