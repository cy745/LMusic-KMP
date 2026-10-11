# 关闭全局主题动画后的 PlayerScreen 排查

## 改动

- `LMusicTheme` 将 `DynamicMaterialTheme.animate` 设置为 false。最终调色板仍随封面主色更新，取消全局逐帧颜色方案发布。
- 保留已有 `rememberPlayerBackgroundColor` 的低刚度 spring；背景颜色 State 继续在绘制期读取。
- PlayerScreen 的封面提供器、采样播放身份提供器、背景色提供器、seed 更新回调分别通过 remember 保持身份。键使用 State/状态容器，回调调用时读取最新值，没有缓存旧歌曲或旧颜色。
- 更正工具栏与背景 helper 的过时注释。临时 SideEffect 输入探针已移除。

本轮没有更改 KRouter 或播放引擎逻辑，也没有增加新的背景动画适配。

## 关闭动画后仍然执行的原因

在专用 Android 模拟器中通过 media_session 切歌，临时 SideEffect 在组合提交后读取输入快照；这些读取不订阅组合状态。日志关联到如下独立变化：

1. 旧歌播放时，播放引擎准备新歌，会短暂发布 `isPlaying = false`。
2. `currentItem` 更新。根作用域直接读取它，用于封面请求与 playbackKey。
3. 引擎恢复 `isPlaying = true`。根作用域读取该值，配置播放器时钟的生命周期任务。
4. 新封面调色板完成，更新 seed，发布最终 MaterialTheme。静态主题 Local 仍会引发一次内容更新。

异步事件可能合并到同一提交，也可能分开。未稳定回调的一轮播放中切歌观察到 4 次根事件；稳定回调后另一轮观察到 3 次，其中歌曲身份与暂停状态合并到同一次。不能把根次数的差异归因于 remember 优化。

暂停时切歌的优化后采样观察到 2 次根事件，对应歌曲身份与最终主题颜色。

duration、歌词就绪、封面请求和 sampledPlaybackKey 也在不同时间更新，但根作用域并不直接在组合中读取所有这些 State 的值。输入探针中它们发生变化，不足以证明它们各自触发了一次根执行。

本次没有发现根作用域随播放进度或局部背景动画持续执行的现象。仍存在异步业务状态的必要更新，不能把“切歌根作用域只能一次”作为固定测试预算。

## 确认并修复的额外传播

仅关闭全局动画后，PlayerScreenContent、Header、BlurBackground 和 Seekbar 仍随根作用域执行 3～4 次。Analyzer 日志每次都报告四个传入回调变化。

检查当前 JVM 字节码也确认：两个闭包直接通过 new 创建，另两个捕获 lambda 通过 invokedynamic 创建；这些调用点没有对应的 rememberedValue/updateRememberedValue 缓存。稳定性报告中 Function 类型标记为 stable，并不等于每次执行会得到同一个函数对象。

因此这里有一条独立的放大路径：

```text
歌曲 / 播放状态更新
  → PlayerScreen.Content 执行
  → 四个回调重新创建
  → PlayerScreenContent 参数变化
  → Header、BlurBackground、Seekbar 再次执行
```

显式 remember 后，日志中四个回调身份保持不变，下游可以跳过仅由父作用域执行引发的更新。此证据没有确认自动 memoization 缺失究竟来自编译器还是 Analyzer 插桩，不应据此断言某个插件存在缺陷。

## 真实采样

所有采样都使用 lmusic_test 模拟器、Kotlin 2.4.20、Analyzer 0.15.0 与 `-Plmusic.trace.player=true`。关闭主题动画后的采样如下；歌曲不同、异步调度不同，数字是诊断证据，不是性能基准或通用预算。

| 作用域 | 回调未稳定：播放中切歌 | 回调稳定：暂停中切歌 | 回调稳定：播放中切歌 |
| --- | ---: | ---: | ---: |
| PlayerScreen.Content | 4 | 2 | 3 |
| PlayerScreenContent | 4 | 1 | 1 |
| PlayerDynamicHeader | 4 | 1 | 1 |
| BlurBackground / DefaultBlurBackground，各自 | 4 | 1 | 1 |
| SeekbarLayout | 4 | 1 | 1 |

下游唯一一次事件与最终 seed / 主题变化处于同一阶段，四个回调没有变化。封面请求 State 与歌词仍正常更新，下游自己读取状态的作用域负责处理，不能用上述函数计数代表所有子作用域的工作量。

移除临时探针后的最终 APK 采样：根作用域 2 次，上表五个下游作用域各 1 次，确认结果不依赖临时探针。上表保留本地采样结论；原始日志与临时 JSON 不纳入版本控制。

此前开启动画的 6 次采样保留为历史记录，未做同一歌曲与完全相同条件下的配对实验，不能宣称固定降低了某个百分比。

## 自动化验证

新增使用实际 LMusicTheme 与实际播放器背景 helper 的 UI 回归测试：

- 从红色 seed 切到蓝色 seed，背景宿主只增加一次 SideEffect 提交。
- 过渡中的截图颜色与初始颜色不同，也与最终颜色不同，证明局部 spring 动画仍然存在。
- 继续推进动画时钟，没有逐帧增加组合提交。

原有静态主题方案替换诊断、MaterialKolor 开关对照等测试保留，帮助区分库行为与应用配置。

最终移除探针后的 Android 诊断构建成功，上述两个测试类共 7 项测试全部通过（0 failures / errors）。专用测试模拟器已关闭。

```bash
./gradlew :androidApp:assembleDebug -Plmusic.trace.player=true \
  :lplayer:jvmTest --tests '*MaterialKolorRecompositionTest' \
  --tests '*PlayerBackgroundColorRecompositionTest'
```

## 后续修复：隔离播放状态读取（2026-10-10）

已将播放器时钟生命周期任务的 isPlaying 读取移到独立 `PlaybackPositionEffect`。`rememberPlaybackPositionState` 接收 `State<Boolean>`，根作用域不再读取它的 value；返回的播放器时钟仍通过 remember 保持身份。手机与平板入口同步使用新接口。

编译器报告与稳定性基线确认 `PlaybackPositionEffect` 为 restartable / skippable；返回状态的 factory 仍不可独立 restart，因此关键是 Unit effect 的读取位置。

原生命周期行为保留：切歌/恢复时同步媒体身份与进度，播放时按帧读取，暂停时每 200ms 读取以接收晚到的 seek 或历史位置，离开前台取消任务。位置读取函数通过 rememberUpdatedState 更新，避免长期任务捕获过时函数。

新增 `PlaybackPositionRecompositionTest` 的两项 UI 测试验证：

- 播放、暂停与进度更新不增加父作用域提交，时钟身份不变；暂停后迟到的 seek/历史位置仍被采样。
- 切歌正确更新 sampledPlaybackKey 与进度；离开前台停止轮询，恢复前台立即同步最新位置。

新时钟测试、主题/背景测试，以及 HistoryQueueRestorer、HistoryRestoreSettlement、HistoryPositionSelection 共 **39 项 JVM 测试全部通过**。Android 诊断构建成功；稳定性 dump/check 成功，涵盖 JVM、Android、iOS Arm64/Simulator Arm64 和 Wasm 的公共代码编译。此处不代表所有平台都完成运行时测试。

模拟器单独播放/暂停的采样只有 2 次 PlaybackPositionEffect 事件，PlayerScreen 根作用域与上述下游均为 0 次。播放中切歌第一次验证以及后续连续 3 次切歌：根作用域均为 **2 次**，PlayerScreenContent、Header、BlurBackground、Seekbar 均各 **1 次**。封面和歌曲显示正常更新，进度仍推进。

连续三次固定 3 秒采样窗口均通过脚本预算：根 ≤ 2，下游各 ≤ 1。预算仅用于此次本地测试曲库条件；远程加载、数据源刷新等场景不能直接套用。

剩余两次根事件仍对应歌曲身份与最终主题更新。静态主题 Local 的最终更新保留；本轮没有改造 Material3 主题边界，也没有在播放引擎层合并或延迟业务状态。
