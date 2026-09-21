# 播放队列完善验收清单

此清单记录当前用户确认的范围；未勾选项目不得用局部测试或编译通过替代完成证明。

当前阶段结论见 [#14 / #17 合并检查点](player-queue-issue-progress.md)。下文按时间倒序保留当时的记录，“未提交/未推送”和旧的待办描述不是最新状态。

## 对账审计与本轮收口（2026-09-21）

对 #14 / #17 声称的 26 个子项逐条回代码核验后发现 8 处表述与实现不符，本轮修掉其中会影响行为的 2 处，并补上 #17 验收标准里明确列出却一直没有用例的 3 项。

### 修正的两处行为缺陷

- **iOS 播放中途失败没有"沿方向"**（原声称"沿方向，与安卓一致"）。`navigateAfterStalledFailure` 只调 `skipToNext()`，而 `AVPlayerPlayback.skipToNext` 把方向写死 `PlaybackDirection.Forward`；Android 的 `onPlayerError` 则沿用最近一次导航开启的 traversal 方向，所以按"上一首"进入的歌曲在 iOS 上会向前跳。
  - 改动：新增公共规则 `stalledFailureNavigationDirection`（三个守卫在导航真正发起时复查：引擎仍是同一实例、仍是被加载项、仍是队列当前项）；`AVPlayerPlayback` 记下"当前已加载项是用什么方向载入的"，失败时沿同方向继续。该字段属于已加载项的状态（同 `loadedMediaKey`/`playRequestedFor`），命令自身方向仍只随 `PlaybackNavigationContext` 走，没有引入命令间的共享可变状态。
  - 测试：`LoadFailureWatchTest` 新增 2 项（方向跟随 + 三个守卫任一不成立即不接管）。
- **解码失败的用户文案不可达**（原声称"解码错误也会抛出可分类异常"）。实测解码错误不抛出，只写状态并发事件；更关键的是 `PlaybackFailureReason.Decode` 全仓只被测试引用，三个分类器都不产出它，于是"音频解码失败，文件可能已损坏"这条文案永远不会出现。
  - 改动：分类器补 `AVAudioPlayerDecodeError` → `Decode`。该标记由 `AVAudioPlayerEngine` 自己构造（`onDecodeErrorDidOccur`），是稳定判据，且位置在 `OSStatus error` 之前，按"取位置最靠前"的既有规则能压过 `UnsupportedFormat`。
  - 测试：`IosLoadFailureTest` 新增 1 项，同时钉住"压过 OSStatus"这条优先级。

### 补上 #17 验收标准里缺失的 3 个用例

`SandboxSourceFileOperationsTest` 由 13 项增至 16 项（`:lmedia:lmedia-core:jvmTest` 实跑全部通过）：

- `concurrentImportsSerializeWithoutLosingIndexEntriesOrLeavingHalfFiles` —— 并发导入 4 份不同内容，断言**索引条目数**等于导入数。串行化一旦丢失（索引"读-改-写"交错），文件系统看起来仍然正常，只有索引会少条，所以只数文件是测不出来的。
- `sameNamedImportsFromDifferentFoldersAllocateNonConflictingNames` —— 不同目录下的同名文件，第二个必须落到 `external (1).mp3`。`allocateDestination` 的 `" (1)"` 分支此前没有任何用例覆盖（只有重命名冲突有）。
- `contentThatIsNotAudioIsRejectedByMagicNumber` —— 扩展名是 mp3、内容不是音频，必须在落盘前被 MagicNumber 拒绝并清掉临时文件。已有的"解析失败"用例只模拟了 Taglib 返回 null，走不到这条分支。

### 本轮验证证据

- `:lmedia:lmedia-core:jvmTest`：`SandboxSourceFileOperationsTest` **16 项通过**（原 13）；`:lplayer:jvmTest` **292 项通过**（原 290，+2），失败 0、跳过 6（平台守卫）。
- iOS 原生（Mac mini，iPhone 17 Pro / iOS 26.5 模拟器，`scripts/test-ios-player-native.sh`）：**7 个测试类 74 项、`testFailed` 0**（原 71；本轮 +3）。新增用例 `aStalledFailureContinuesAlongTheDirectionTheSongWasLoadedWith`、`aPlaybackTimeDecodeErrorIsReportedAsDecodeRatherThanAFormatProblem` 均在模拟器内实跑。
- Android 设备测试（Android 16 / API 36 模拟器，真实 Media3）：**5 个类 22 项全部通过**（`QueueControlPlayerDeviceTest` 9、`QueueAutomaticTransitionDeviceTest` 4、`QueueDeletionRecoveryDeviceTest` 4、`QueueRecoveryControlDeviceTest` 3、`QueueFailureTraversalDeviceTest` 2）。
  - ⚠️ `./gradlew :lplayer:connectedAndroidDeviceTest` 在本环境报告 `Starting 0 tests`，用例一个都没被发现（测试 APK 已正确生成，包名 `com.lalilu.lplayer.core.test`）。绕过方式已写进 `docs/testing-guide.md` §5.3。该任务未发现用例的原因尚未定位，不把它当作"通过"。
- **Android 外部打开端到端（真机链路，模拟器执行）**：把一段真实 MP3 推到 `/sdcard/Download/`，用 `am start -a android.intent.action.VIEW -d file://… -t audio/mpeg` 触发冷启动，实测完整链路成立：
  - `MainActivity` 启动 → Koin 注册 `AndroidSandboxMediaSource`；
  - 文件落到应用私有沙箱 `files/media-sandbox/Imported/…`（240579 字节，`content_digest=e81290eb…`，`duration=30041`，`available=true`），即**外部原件未被移动**；
  - 得到来源限定身份 `audio_…` @ `SandboxFileSystemSource`；
  - `MServiceCallback onAddMediaItems` 收到该项，`dumpsys media_session` 报 `state=PLAYING(3), position=10458, error=null`；
  - 截图确认播放页显示该曲、进度 00:12 / 00:30，封面位为灰色占位（该文件无内嵌封面），界面无破损。
  - 说明：`file://` 意图在系统里同时匹配到 YouTube Music，`am start` 因此弹出选择器；改用显式组件（`-n`）绕过选择器后链路正常。清单里的 `audio/*` 过滤器本身已被系统正确解析出 `com.lalilu.lmusic.MainActivity`。

### 结案：`MediaCoverRequest` 的 "Unable to create a fetcher"

本轮顺手把这条一直挂着的待复核项查清了，**结论是它不是 bug**：

`EngineInterceptor.fetch` 是一个循环——`components.newFetcher(data, …, searchIndex)` 从当前下标往后找工厂，全部返回 null 后 `checkNotNull` 抛出 `Unable to create a fetcher that supports: <data>`。`MediaCoverRequestFetcherFactory.create()` 确实被调用并返回了 `LAudioFetcher`，但它的 `fetch()` 在 `resolvePicture(...)` 返回 null（**这首歌没有内嵌封面**）时按 Coil 约定返回 `null` 表示"交给下一个工厂"，而没有任何其他工厂认识 `MediaCoverRequest`，于是循环走到底抛出该文案。

也就是说：**注册是生效的，异常的真实含义是"这首歌没有可读封面"**，属于预期路径；界面按占位降级（截图已确认）。因此不改代码。若要减少日志噪音，属于"把 Coil 的 error 级降级"这类改动，会把真正的问题一起盖住，本轮不做。


### 仍未完成（与核对结论一致）

- iOS `AVAudioPlayerEngine` 的**解码错误路径没有设备用例**：`audioPlayerDecodeErrorDidOccur` 没有被任何测试触发过（本轮只让它的分类可达）。
- iOS 重复投递同一个打开事件**没有加守卫**：核验后确认 iOS 只有单一 `WindowGroup` + 单个 `.onOpenURL`，无多场景/多窗口声明，重投递风险没有证据支撑；而重复导入在数据层已经幂等（内容摘要去重 + `selectOrInsert` 复用），因此按"最小改动、不引入抽象"没有加投机性守卫。Android 侧的 `savedInstanceState` 守卫已核。
- 失败跳过整体仍在 `queueEditMutex` 内（有 60s 预算兜底），结构性解耦未做。
- MediaBrowser / 通知栏 / Room 慢查询的完整竞争矩阵仍无用例。
- MusicKit 迟到异步错误的 storeID / 加载代次归属未做（建议记录不改）。
- 真机（小米 / iPhone / iPad）运行验收、Desktop 与 Web 冒烟未做。
- 应用层 `SandboxFileOperationsImpl`（文件 + 数据库 + 队列联合事务）仍无直接用例；`ExternalAudioOpenCoordinator` 与 `MainActivity` 同样无测试。二者都依赖全局 `LPlayer.instance`，补测需要先有注入缝，本轮不做。

## 选曲不再被原生加载阻塞（Desktop 半，2026-09-14 后续轮次）

- 根因与 iOS 同构但位置不同：`prepareVlcMedia` 在命令内等待原生进入 PAUSED（30s 上限）与定位确认（5s），这段位于队列编辑边界内。
- 先用真实 VLC 实测可观察状态（临时诊断用例，跑完即删）：正常文件 → `PAUSED` 且音轨 >= 0；坏内容/不存在的文件 → **在从未进入 PAUSED（音轨 -1）的情况下直接 `ENDED`**；不可达地址 → 长时间停在 `OPENING`。据此确定"就绪 = PAUSED + 有音轨；坏 = 未就绪即 ENDED；卡住 = OPENING 超时"。
- 改动：拆出非阻塞的 `startVlcMedia`（停止旧输入 + 提交描述符 + 触发打开并立即返回），会话路径改用它；常驻观察者接管"就绪后按需真正播放 / 坏内容与超时按歌曲记账 / 用户要求过播放时沿方向继续下一首 / 真实推进后清除失败"。历史恢复保留阻塞式 `prepareVlcMedia`（该路径不在队列编辑边界内）。
- 顺带修掉两个由此暴露的问题：①**就绪前的定位请求**改为挂起、由观察者在就绪后补上（VLC 在 OPENING 时 `setTime` 会被忽略），因此 `updatePlaylist(start=false) + seekTo` 不再依赖阻塞式预备；②**就绪后补播必须重新检查播放意图**——既有用例 `pausedSeekAndHistoryRecordingStayAtTheNativePosition` 恰好抓到了这个回归（准备期间暂停后又被补播），已修并新增 `pauseBeforePreparationCompletesCancelsTheQueuedAutoplay`。
- 验证（本机真实 VLC + 真实 MP3 夹具）：Desktop 原生新增 4 项——慢加载不阻塞下一次选曲（不可路由地址，断言两次选曲均 <2s 返回）、坏文件记账并跳到下一首、被替换歌曲的迟到失败不劫持当前播放（2s 后仍在播且未把失败记到当前歌）、准备期间暂停取消补播；`VlcPlaybackNativeTest` + `VlcHistoryNativeTest` 合计 **24 项、失败 0**，连续两轮复跑稳定。JVM 全量 **266 项、失败 0、跳过 0**（`/tmp/lmusic-goal-jvm2.log`）；iOS 原生 **7 类 71 项**、`testFailed` 0（`/tmp/lmusic-goal-ios3.log`，新增"选曲后立刻定位仍能落到目标"以钉住 AVFoundation 自身排队的行为）；`composeApp` iOS/JVM、`lplayer` Wasm、`androidApp` Android 编译通过。
- 仍未完成：没有在 Windows/Mac 上手动点验（本机验证的是逻辑与真实播放器边界）；上述 VLC 状态事实来自 macOS + 仓库自带 VLC 构建，其他平台/版本未复测；Android 本来就异步，未改动。

## 选曲不再被原生加载阻塞（iOS 半，2026-09-14 后续轮次）

- 问题实测（公共边界，虚拟时间）：一次慢加载会让**第二次点歌和所有队列编辑排队等待**，且是等待而不是取消；`updatePlaylist(..., start=false)` 这种"只加载不播放"的调用同样占用该边界。测量用例 `QueueEditLockMeasurementTest` 固定了这三条现象与 35s 全占用的观测。
- 根因不是"换歌在锁里"，而是 **iOS 的引擎 `load` 在命令内等待原生就绪**（30s 上限）。安卓是把"等就绪"放到命令之外，所以不受影响。
- 契约修正：`PlaybackEngine.load` 明确为"发起加载，返回不代表可以播放"；新增 `PlaybackEngine.awaitPreparation(timeout)` 供需要在命令内确认就绪的调用方（历史恢复）显式等待，默认实现轮询状态，`AVPlayerEngine` 覆盖以保留"取消时只释放仍属于本次加载的媒体"。`AVPlayerEngine.load` 不再等待；历史恢复改为 `load + awaitPreparation`。
- 验证：新增 iOS 原生用例"慢加载不阻塞选曲命令"（不可路由地址，断言 `< 2s` 返回）与"缺失文件经状态暴露而不是阻塞返回"。把等待临时放回 `load` 后，前者以 `Timed out waiting for 30000 ms` 失败（red，`/tmp/lmusic-goal-ios-red.log`），恢复后 iOS 原生 **7 类 70 项**、`testFailed` 0（`/tmp/lmusic-goal-ios1.log`）。JVM 全量 **262 项、失败 0、跳过 0**（`/tmp/lmusic-goal-jvm1.log`）。
- **Desktop 尚未修复**：`prepareVlcMedia` 仍在命令内等待原生 PAUSED（30s）与定位确认（5s），因此电脑版仍会被慢加载阻塞。下一步：会话路径改为"只发起加载"，由常驻观察者接管"就绪后按需播放 / 失败上账 / 停住导航"，历史恢复保留阻塞式准备。完成后用本机真实 VLC 原生测试验证（慢加载不阻塞、迟到完成不劫持当前播放、坏文件自动跳过）。

## iOS 播放中途错误的自动导航（2026-09-14 后续轮次）

- 用户确认要"和安卓一致：自己跳下一首"。新增纯规则 `shouldNavigateAfterStalledFailure`，要求三个条件同时成立才导航：**用户确实要求过播放这首歌**（暂停/停止后出现的错误不算，避免用户想停下来观察时被跳走）、失败项仍是当前加载项与队列当前项（迟到错误或用户已换歌都不接管）、位置在两次采样之间**没有前进**（确认真的停住，而不是加载慢或缓冲）。
- `AVPlayerPlayback` 增加 `playRequestedFor`：`attemptLoad(start=true)` 与被要求播放的复用分支写入，`pauseInternal` / `stopInternal` 清除。观察器在错误分支按上述规则判定，命中则交给独立协程执行 `skipToNext()`（观察器本身会在换歌时被取消，不能让它把导航一起带走）；导航仍走公共跳过循环，因此失败项不会在同一轮被重复尝试。
- 规则测试 4 项：停住且已请求播放才导航；仍在前进不跳；暂停/停止后不跳；迟到错误或已被替换的歌曲不跳。
- 验证：`:lplayer:jvmTest` 全量 **259 项、失败 0、跳过 0**（`/tmp/lmusic-r6-jvm.log`）；iOS 模拟器原生 **7 类 69 项**、`testFailed` 0（`/tmp/lmusic-r6-ios-native.log`）；`composeApp` iOS/JVM、`lplayer` Wasm、`androidApp` Android 编译通过。
- **未验证**：本机无法让 iOS 真实播放中途出错（需要真机 + Apple Music 订阅），因此"观察器判定 + 自动跳转"的**接线**没有自动化覆盖，也没有设备端验收；判定逻辑本身只有规则级测试。若判定失误，表现为"该跳没跳"或"不该跳却跳了"，回滚这一处即可。

## 电脑版复用公共失败跳过（2026-09-14 后续轮次）

- `VLCPlayback.selectInternal` 不再自己维护一份简化循环（每次选曲新建 traversal、无失败表过滤、无总时长上限），改为与 iOS 共用 `navigateWithFailureFallback`：候选槽位经 `playablePlaybackSlots` 过滤（可用 + 来源就绪 + 未记录失败），并有相同的 60s 总预算（`DefaultSkipNavigationBudget` 提到公共层，iOS 改用它）。
- 记账收敛到调用方：`playItem` 只负责加载；选曲路径的每个失败由循环的 `recordFailure` 记账，历史恢复路径在 `restoreInternal` 的 catch 中记账后继续上抛（与 iOS 同构）。`isLoadedItem` 的判定在尝试之前发生，因此对未初始化播放器做了 `runCatching` 保护，避免抛出绕过失败处理。
- 与旧实现的行为差异：队列在两次尝试之间只有元数据刷新时不再中止（比较按 playbackId 槽位，而不是整个 QueueState 结构相等）；已记录失败的歌曲在跳过时不再重试。
- 验证（仓库内 VLC 资源 + 真实 MP3 夹具，本机实跑）：`:lplayer:jvmTest` 全量 **255 项、失败 0、跳过 0**（以前 19 项原生 VLC 用例因未配置环境而跳过）——`/tmp/lmusic-r5-jvm.log`。新增原生用例 `alreadyRecordedFailuresAreNotRetriedWhileSkipping`：去掉失败表过滤后实际尝试序列退化为 `[bad, remembered, good]` 并失败（`/tmp/vlc-red.log`），恢复后为 `[bad, good]`（`/tmp/vlc-newtest.log`）。基线（改动前）同一套原生测试 19 项全过，对比结果一致。iOS 模拟器原生 **7 类 65 项**、`testFailed` 0（`/tmp/lmusic-r5-ios-native.log`）；`composeApp` iOS/JVM、`lplayer` Wasm、`androidApp` Android 编译通过。
- 仍未完成：这是逻辑与真实播放器边界的验证，不等于在 Windows/Mac 上手动点过一遍；Desktop 的跳过语义变化（上述两条差异）未做人工验收。

## 清零守卫重估与候选槽位规则抽取（2026-09-14 后续轮次）

- **清零守卫改为锁内重估阶段**（原低危 L9）：原先用恢复阶段的**对象标识**比较，StateFlow 合并等值通知时会把一次本应生效的清零整体丢掉，让陈旧进度留到下次恢复。现在锁内按**当前值**重新判定 `isFallbackClearPhase`（Pending && currentRestored && currentId == null），保留队列身份与"此后没有更新的位置采样"两个条件。新增用例要求"结构相等的新恢复阶段实例仍然清除"，并固定"历史 current 已解析 / 阶段已结束时不得清零"。
- **候选槽位过滤抽成公共规则**（原测试缺口 L10）：`playablePlaybackSlots(list, recordedFailures, sourceReady)` 从 iOS 实现里抽出，新增 3 项测试覆盖可用性、来源就绪、已记录失败，以及"同原始 ID 不同来源的失败不互相排除"。此前测试里的假实现只替掉调用，测不到真实过滤条件。
- 验证：`:lplayer:jvmTest` 全量 **254 项**、失败/错误 0、跳过 19（`/tmp/lmusic-r4-jvm.log`）；iOS 模拟器原生 **7 类 65 项**、`testFailed` 0（`/tmp/lmusic-r4-ios-native.log`）；`composeApp` iOS、`lplayer` Wasm、`androidApp` Android 编译通过。

## 审查缺口收口：分类器配对、Bytes 失败上报、跳过总时长上限（2026-09-14 后续轮次）

- **分类器 domain/code 成对匹配**（原低危）：`iosFailureReasonFromDescription` 改为成对正则匹配，并按**出现位置最靠前**的一组判定——`NSError.description` 顶层 `Error Domain=… Code=…` 在前、`UserInfo` 嵌套在后，因此嵌套的 `NSCocoaErrorDomain Code=257` 不会再覆盖外层的网络错误；同时兼容 `localizedDescription` 的 `(NSURLErrorDomain error -1009.)` 形式（MusicKit 用它），并新增 `NSOSStatusErrorDomain`/`OSStatus error` → `UnsupportedFormat`。回归用例"顶层网络 + UserInfo 嵌套 Cocoa 257"在退回"按类别顺序取首个命中"时返回 `PermissionDenied`（`/tmp/lmusic-r3-ios-red.log`），恢复成按位置取最早后通过（`/tmp/lmusic-r3-ios-native.log`）。
- **Bytes 播放失败不再静默**（原中危）：`AVAudioPlayerEngine.load` 以前不读错误指针、不查 `prepareToPlay()` 返回值、解码错误只打日志，于是 Bytes 播放失败既不上报也不入账。现在空数据、构造失败（Kotlin/Native 对返回 nil 的 ObjC 构造器抛 NPE，真正原因在错误指针里，需捕获后读出）、`prepareToPlay` 失败都抛可分类异常并写入 `state.error`；`onDecodeErrorDidOccur` 写入状态并上报一次 `PlaybackEngineEvent.Error`。新增 `AVAudioPlayerEngineTest` 用真实 AVAudioPlayer 固定坏数据/空数据必须失败、合法 16bit PCM WAV 必须按时长成功加载（防止"改成永远抛错"也算通过）。修复前该用例以 `Expected IllegalStateException but was NullPointerException` 失败。
- **跳过总时长上限**（原中危）：`navigateWithFailureFallback` 新增每次尝试前求值的 `outOfBudget`；iOS 以 `TimeSource.Monotonic` 给单次失败导航设 60 秒上限，超时按预算用尽处理（停止 + 抛出首个失败并附 suppressed 说明），避免在队列编辑锁内把单次加载 30s 放大成 N×30s。规则测试 `anExhaustedTimeBudgetStopsInsteadOfTryingEverySlot` 去掉该分支即失败（`/tmp/nav-budget-red.log`）。
- 验证：`:lplayer:jvmTest` 全量 **250 项**、失败/错误 0、跳过 19（`/tmp/lmusic-r3-jvm.log`）；iOS 模拟器 iPhone 17 Pro（iOS 26.3）原生 **7 个测试类 65 项**、`testFailed` 0（`/tmp/lmusic-r3-ios-native.log`）；`composeApp` iOS、`lplayer` Wasm、`androidApp` Android 编译通过。
- 仍未完成（与上一节一致）：iOS 播放中途错误的自动导航；迟到异步错误的 storeID/加载代次归属（需要引擎级加载令牌，MusicKit 的 Error 事件目前不带条目信息）；"坏文件 → 红卡片 → 重试 → 清错 → 自动跳下一首"的设备端到端验收；Desktop 仍未复用公共跳过循环。

## iOS 失败后沿方向跳过（2026-09-14 后续轮次）

- 新增公共内部函数 `navigateWithFailureFallback`：单次导航内沿方向跳过失败槽位，最多绕队列一周；跳过前重新读取队列，被替换即中止而不推测新的所有权；预算用尽时先 `stop` 再抛出首个失败，每个失败槽位单独回调记账。`skipPolicy` 在**尝试之前**求值——只有"用户请求了播放且这次是真正的加载"才允许跳过，已经加载成功后的失败不会升级成整队列连跳。
- `AVPlayerPlayback.selectInternal` 改用该循环：候选槽位需 `available`、来源已就绪、且不在失败表中（一次跳过搜索只读一次失败表）；失败槽位按 `classifyIosLoadFailure` 记录后继续沿方向尝试，全部失败则停止并上报首个失败。`attemptLoad` 抽出复用分支与全新加载分支，成功后启动观察器；引擎处于错误态时不再走"同一首已加载"捷径。
- 方向来自 `PlaybackNavigationContext`：iOS 现在与 Desktop 一样在 `skipToNext`/`skipToPrevious` 中写入 Forward/Backward；自然播放结束走同一条命令路径，因此"自动切到一首坏歌"也被覆盖。
- 明确不做：**播放中途**（已加载后）引擎报错只记录失败、不自动切歌。Android 的 `onPlayerError` 会导航，但 iOS 引擎报错时会同时把状态置为未播放，无法可靠区分"用户在暂停时遇到的错误"与"播放中断"，自动切歌可能违背用户当前意图；且 MusicKit 会同时置 `state.error` 与发 Error 事件，两处都导航会连跳两首。此项作为已知跨平台差异保留。
- `/tmp/lmusic-final-jvm.log`：`:lplayer:jvmTest` 全量 248 项（新增 19 项导航/观察规则测试），失败/错误 0、跳过 19。`/tmp/lmusic-final-ios-native.log`：iPhone 17 Pro（iOS 26.3）原生运行 6 个测试类共 57 项，`testFailed` 0；脚本 filter 新增 `PlaybackFailureNavigationTest`、`LoadFailureWatchTest`，证明同一套规则在 Kotlin/Native 上同样通过。
- `composeApp:compileKotlinIosSimulatorArm64`、`lplayer:compileKotlinWasmJs`、`androidApp:compileDebugKotlin` 通过。本轮未改 Desktop 的 `VLCPlayback`（仍是每次选曲新建 traversal），公共循环可被它复用但不在本轮；iOS 也还没有 Android 那样的内容就绪等待，只按当前来源状态过滤候选。
- 独立对抗性审查（子 agent，只读）提出并按严重程度处理：
  - **已修（高）**：观察器原来是一次性的，记账后 `return`，而 `playInternal` 的"已加载直接 play"分支不会重启它 → 被记录过失败的歌曲即使用户恢复播放成功，记录也永远清不掉，卡片持续红色并被跳过过滤器长期排除。现在观察器常驻到媒体被替换/停止为止，记账与清除共用同一个 `PlaybackFailureWrites.Ticket`（重新注册会让另一侧被自己的写入判为过期）；判定抽成公共 `LoadFailureWatch` 并配 5 项规则测试，其中"更早段落留下的失败在首次真实推进后也必须清除"直接覆盖该缺陷。同时移除 `recordEngineFailure`：iOS 现有三个引擎的异步错误都会写入 `engine.state.error`，第二个写入者只会抢走 ticket 并再次破坏清除。
  - **已修（中）**：`playableSlots` 是挂起调用（iOS 侧查失败表），原先只在它之前校验队列；同长度重排不会越界，旧下标会被套到新列表的别的槽位上。现在挂起点之后复查候选 id 列表。`PlaybackFailureNavigationTest` 新增"重排后中止"用例，**去掉该复查即失败**（`/tmp/nav-red.log`），恢复后通过（`/tmp/nav-green.log`）。
  - **已修（中）**：失败表读取失败不再顶替原始加载失败，按"没有已知失败"继续尝试并记录诊断（`runCatching`），与 Android 的恢复边界一致。
  - **未修（记录为中）**：跳过循环整体位于 `queueEditMutex` 内（`playAudio` 与 `skipToNext` 的优先请求分支），单次 `AVPlayerEngine.load` 最坏 30s，坏队列最坏 N×30s 阻塞队列编辑且不会被这些操作取消；需要给单次尝试/整轮跳过加总时长上限或把失败导航改成投递新命令，属于结构性改动。
  - **未修（记录为中）**：`AVAudioPlayerEngine` 从不写 `state.error`、decode 错误只打日志、`load` 不检查 `errorPtr`，因此 iOS Bytes 播放失败对新账本基本不可见；需要引擎侧补错误信号。
  - **未修（记录为中）**：迟到异步错误缺 storeID/加载代次判定，MusicKit 上一首条目的迟到 error 可能记到当前歌曲；需要引擎把加载代次带进 Error 事件。
  - **未修（低）**：分类器按 domain + code 独立匹配，嵌套 NSError 的 UserInfo 可能误判；清零守卫用恢复阶段对象标识可能"漏清"而非"误清"（反向风险已确认不可达）。
- 仍未完成：iOS 播放中途错误的自动导航；真实坏文件注入与"红卡片 → 重试 → 清错 → 自动跳下一首"的模拟器/真机端到端验收；上述三个中危缺口。

## 历史回退清零归属与 iOS 失败持久化（2026-09-14 后续轮次）

- 历史回退的“清零 position”通知原先没有归属校验：队列写入与位置采样都会核对发出时的队列/恢复阶段，只有清零直接 `savePosition(0)`。新增 `shouldClearFallbackPosition`，要求队列与恢复阶段仍是通知发出时的同一次（按对象标识比较），且此后没有任何更新的位置采样；`PlaybackHistoryImpl.positionWrites` 在每次采样成功写入后自增，清零进锁后比对计数。`/tmp/lmusic-history-clear-ownership.log`：`:lplayer:jvmTest` 全量 229 项，失败/错误 0、跳过 19（原生 VLC 环境变量缺失）。iOS 模拟器与 Wasm 编译、`composeApp` iOS 编译、`androidApp:compileDebugKotlin` 均通过。
- 该竞态只在真实多线程交错下可达（`recordingMutex` 临界区全为同步代码，`runTest` 单线程调度无法抢占，`HistoryStorage` 也没有挂起缝），因此本轮只有规则级测试，**没有“修复前失败”的复现证据**，不得宣称竞态已在设备上验证消除。
- 按“内测阶段不做数据兼容与迁移”的决定，删除 V1/V2 历史键 `historyPlayPosition`、`historyPlaylistIds`、`historyPlayId`、`historyQueueIdentityV2` 及 `HistoryStorage` 的 `savedPlaylistIds/savedPlayId/savePlaylistIds/savePlayId`。现有持久化只保留 `historyPlaybackQueue`（队列身份 + current + position 的单条 JSON）与 `historyPositionResetRequested`。装过旧版本的设备升级后历史会被丢弃，这是明确选择而非遗漏。
- iOS 接入失败持久化：`AVPlayerPlayback` 注入 `PlaybackFailureRepository` 并复用 `PlaybackFailureWrites`。命令路径的加载失败记账；AVPlayer `status=Failed` 这类不会变成异常的情况由 `watchLoadedItem` 记账（同时负责“真实位置推进后才清除”）；MusicKit 的 Engine Error 事件只在错误来自当前引擎且仍是当前已加载歌曲时记账。来源未就绪、取消、非当前歌曲一律不记账（`shouldRecordIosLoadFailure` 规则测试覆盖）。引擎处于错误态时不再走“同一首已加载”捷径，避免界面重试变成空操作。
- iOS 分类只识别明确的 domain+code（`NSCocoaErrorDomain` 257/260，`NSURLErrorDomain` -1001/-1003/-1004/-1005/-1009）。刻意不把整个 `NSURLErrorDomain` 判成网络问题：不存在的本地文件同样可能报 -1100，类别错误比通用提示更糟，因此保留 Unknown。
- `/tmp/lmusic-ios-failure-native.log`：iPhone 17 Pro（iOS 26.3，`588DC4F8-1B07-4D89-BD2F-862ABBC44113`）上经 `scripts/test-ios-player-native.sh` 原生运行 4 个测试类共 38 项，`testFailed` 0 项；脚本 filter 新增 `IosLoadFailureTest`。首轮曾出现 1 项失败，原因是分类表按合成字符串而非真实 `NSError.description`（`Error Domain=… Code=…`）格式匹配，已修正测试与规则。
- 仍未完成：iOS 失败后没有沿方向有界跳过（只保留失败状态，不自动跳下一首）；iOS 失败链路的真实坏文件注入、界面重试与真机运行验收未做；VLC 异步 error 归属、Desktop 跨选曲失败预算、Android 清错时机仍未改动。

## 删除失败的受保护队列恢复（2026-09-14）

- SandboxFileOperationsImpl 在文件恢复且数据库确认完成后，调用本次删除专属 QueueRemovalRecovery：记录删除前列表/当前重复项位置/可归属进度，恢复时使用最新恢复的歌曲元数据。即使原生应用在队列已发布后抛异常，也保留恢复记录。
- QueueState 增加仅内存 editRevision，区分调序、删除等非选歌编辑；Sync/HistoryRestore 不推进。用户调序后再调回也会使旧回滚失效；显式清空已空队列同样代表新操作。既有 selectionRevision 继续区分同曲重新选择，不改磁盘格式。
- Playback 的 editQueueWithReceipt 在已有编辑锁内记录内部切歌完成后的状态；restoreFailedQueueEdit 只有在记录仍有效时应用。Android 使用同一原生线程上的 restorePausedQueueIfOwned，同时核对原生列表/索引，随后设置原列表、位置并保持暂停；不自动播放。
- 用户新选歌、手动编辑或原生队列/当前项已经变化时不覆盖当前队列。此为保守恢复：包括自动转场/随机重排等导致最终队列不同的情况，也可能跳过恢复，不推测新的队列所有权。旧“下一首优先请求”不属于本次恢复记录。
- 第一轮新增测试暴露内部切歌推进编号后错误拒绝恢复，已改为在编辑边界内记录成功应用后的状态。修复后覆盖重复项/位置/暂停、调序再调回、元数据同步、原生部分失败、同曲新选歌；另补显式清空已空队列测试。
- `/tmp/lmusic-delete-queue-device.log`：Pixel_9a 上新增 3 项原生恢复测试及既有 18 项回归通过，共 21 项（3.061s）；包括空队列恢复重复项到 12345ms 且暂停、拒绝尚未回传的原生新选择、拒绝调序后调回。最终清空边界补充测试与最终构建结果见后续记录。
- 这些测试包含公共恢复会话、真实 Android 播放器及生产恢复函数，但没有通过真实文件系统删除异常 + Room + MediaBrowser 全链路注入，不标记整个 Sandbox 事务验收完成。
- 最终 `/tmp/lmusic-delete-queue-verified.log`：播放器全部 commonTest 共 195 项通过；Sandbox 4 个文件测试类共 27 项有效回归结果；Release 和设备测试包构建成功（2m13s）。`/tmp/lmusic-delete-queue-device-final.log`：Pixel_9a 最终 22 项真实 Media3 测试通过（2.596s），含已空队列再次清空后拒绝恢复。最终 Release 已覆盖安装小米 `74e1826f` 及 Pixel_9a，均 Success，未操作小米界面。`git diff --check` 通过，未提交或推送。

## Pixel 9a 删除正在播放的 Sandbox 文件（2026-09-14）

- 在已确认的 Pixel_9a（emulator-5554）安装当前 Release，仅复制此前用户授权的 MP3 测试副本到 `/sdcard/Download/LMusic-Sandbox-Deletion-Test.mp3`（4959076 字节），未操作小米原文件。
- 首次通过 file URI 打开因模拟器未授予音频读取权限而得到 EACCES，未当作成功；授予 READ_MEDIA_AUDIO、扫描测试文件后，为强制走 Sandbox 路径临时关闭 Android 媒体库来源，再次打开成功。MediaSession 为 PLAYING、position=6068ms、error=null，Sandbox 页面确认 1 个导入文件。
- 在实际 Sandbox 页面点击删除并确认永久删除这个测试导入副本。随后页面为 0 个文件，MediaSession queue size=0、state=NONE、error=null；下载目录外部副本仍有 4959076 字节，可重新导入。只删除了 App 保存的测试副本。
- 已查看 `/tmp/lmusic-sandbox-before-delete.png` 与 `/tmp/lmusic-sandbox-after-delete.png`，标题、文件名、操作按钮和删除后空态正常显示。最后已重新启用 Android 媒体库来源，并确认开关开启、状态已就绪；音频读取权限保留用于后续模拟器验收。
- 本次没有修改生产代码，也没有新构建或提交。正常导入→播放→界面删除→队列清空链路通过；未故障注入，不能证明失败回滚。
- 代码审计确认未完成项：文件/数据库恢复回调目前不补回已移除的队列项。后续回滚必须先确认队列仍属于本次删除，不能覆盖用户期间新选歌或手动编辑；当前不标记删除事务全部完成。

## Sandbox 操作来源限定查询（2026-09-14）

- 审计发现 ExternalAudioOpenCoordinator 的打开确认/导入提交，以及 SandboxFileOperationsImpl 的重命名/删除确认仍有 4 处原始 ID 单条查询。跨来源同 raw ID 时，DAO 按设计返回 null：导入/改名可能超时，删除则可能过早当作目标已不可用。
- 四处统一改用 `getAudioByPlaybackId`，保留既有来源、路径、revision 和队列确认检查；external 目录不再有该类原始 ID 单条查询。
- ExternalAudioCommitTest 新增同 raw ID 双来源的新路径确认，测试仓库禁止原始 ID 读取，限定查询后能正确确认 Sandbox 条目。
- `/tmp/lmusic-sandbox-qualified-operations.log`：8 项导入提交测试、12 项文件操作、4 项身份、6 项原子写入、5 项分阶段删除测试通过，共 35 项，无失败/错误/跳过。测试在 JVM 上验证公共/nonWeb 文件逻辑，不涉及 Desktop 播放器/iOS 验收。
- 此批不证明 SandboxFileOperationsImpl 与真实数据库/播放器完整事务、正在播放文件的界面删除或失败后的队列恢复已全部通过，仍须端到端验收。
- 同一构建 Release 成功（1m36s），覆盖安装小米 `74e1826f` 返回 Success；未操作小米界面，未提交或推送，`git diff --check` 通过。

## 公共歌曲 UI 来源隔离（2026-09-14）

- PlaylistLayout 传给 SongCard 的共享动画 ID 从原始 id 改为 playbackId，和队列 key、详情路由保持来源限定，避免不同来源同 raw ID 的标题/封面共享标识冲突。
- SourceRecoveryUiTest 新增两个来源同 raw ID 的交互回归：失败状态只属于对应来源；关闭来源后行/封面点击与长按禁用；另一来源不受影响；重启来源保留失败；模拟成功清除记录后恢复正常。
- `/tmp/lmusic-source-ui-regression.log` 中 6 项公共 Compose 组件测试通过，无失败/错误/跳过。使用已有 JVM UI 测试宿主，不涉及 Desktop 播放器或 iOS 验收。此证据不替代各完整页面的真机截图/交互和真实播放成功清错链路。
- 同一构建 Release 成功（1m7s），已覆盖安装小米 `74e1826f`，Success；未操作手机界面。`git diff --check` 通过，未提交或推送。

## Pixel 9a 控制交错回归（2026-09-14）

- 用户清理了旧模拟器，后续只使用 `Pixel_9a`。本次通过 `adb emu avd name` 确认 `emulator-5554` 对应此 AVD，后台无窗口启动；旧设备编号及旧模拟器音频夹具不能继续假定存在。
- 新增 QueueRecoveryControlDeviceTest，使用真实 ExoPlayer、生产 QueueControlPlayer/失败遍历/异常保护，挂起模拟数据库操作，验证当前失败暂停，以及暂停后恢复、停止后恢复不会被旧查询再次暂停。
- `/tmp/lmusic-pixel-recovery-device.log`：3 项新增测试及 15 项原生队列回归全部通过，共 18 项（2.326s）。测试通过直接调用生产控制包装层执行，不经过通知栏/MediaBrowser，也不使用 Room 故障注入；不能替代完整系统控制端到端验收。
- `/tmp/lmusic-pixel-recovery-build.log`：设备测试包与 Release 构建通过（31s，Release 使用有效增量产物），已覆盖安装小米 `74e1826f`，Success。此次只新增测试，未修改生产行为，未操作小米界面，未提交或推送。`git diff --check` 通过。

## 同曲同槽重新选择的历史归属（2026-09-14）

- 实际启动 PlaybackHistoryImpl 的回归测试复现：旧进度查询挂起后，以相同列表/index 再次 updatePlaylist，旧 42000ms 仍进入历史快照。`/tmp/lmusic-same-selection-red.log` 为修复前失败证据（QueuePlaybackTest.kt 第 48 行）。
- QueueState 增加仅驻内存的 selectionRevision；有效 switchTo 或指定 index 的 replaceAll 在非 Sync/HistoryRestore 请求中推进一次，使 StateFlow 不再合并“内容相同但属于新选歌”的状态。编号不进入数据库、不改变歌曲身份或列表顺序。
- Sync 镜像、历史逐步补齐不增加编号，无效 index 不增加编号；已有快照归属检查据此拒绝旧进度。新增实际录制器测试要求拒绝 42000ms 并在后续保存新一次播放的 7000ms，另补队列/平台桥的编号与无操作回归。
- Android 失败处理也检查错误到达时捕获的 selectionRevision，公共队列已接受新选歌但原生命令尚未应用时，旧失败处理不能继续跳歌或暂停。
- `/tmp/lmusic-same-selection-green.log`：64 项针对性回归通过，Release 和设备测试包构建成功（1m41s）。`/tmp/lmusic-same-selection-common-regression.log`：播放器全部 commonTest 测试类共 189 项通过，领域层 83 项使用有效 Gradle 缓存结果，均无失败/错误/跳过；未运行 Desktop/iOS 测试。`/tmp/lmusic-same-selection-device.log`：15 项既有 Media3 设备回归通过（2.881s）。
- 此项针对经过公共选歌边界的重新选择，不宣称已验证所有原生同曲重载、系统控制或失败回调交错。
- `/tmp/lmusic-same-selection-final.log`：包含 Android 失败编号检查的最终 Release 构建通过（1m38s），覆盖安装小米 `74e1826f` 返回 Success，未操作小米界面；未提交或推送。最后的 Android 检查接入只有编译验证，完整 MediaBrowser 故障交错仍待运行验收。

## 当前验收范围调整（2026-09-14）

- 用户明确本轮先完善剩余功能，以公共逻辑和 Android 验收通过为准；Desktop/iOS 运行验证暂缓，不阻塞本轮验收，也不将其标记为通过。
- 保留已有 Desktop 改动，不继续扩展它的异步错误处理或 iOS 错误接入。
- 优先顺序：历史记录过时任务保护 → Android 错误处理/暂停竞争 → 各歌曲入口状态与来源/Sandbox操作回归 → 固定源码综合测试及 Release 安装小米。
- 本轮首步为历史队列写入增加队列与恢复阶段归属检查，旧 Flow 通知不能写回已被替换的状态。此检查不等于已解决 StateFlow 合并相同值时的同曲重装代次问题，独立恢复清零通知也仍需进一步审计。
- `/tmp/lmusic-android-history-ownership-final.log`：4 项归属规则测试、22 项公共队列测试、19 项恢复测试全部通过（共 45 项）；最终 Release 构建成功，1m38s。已覆盖安装小米 `74e1826f`，返回 Success，未操作手机界面。本批未做设备交互验收，不代表剩余功能全部完成。

## Android 失败查询保护（2026-09-14）

- `MPlayerPlayback.onPlayerError` 在错误到达时捕获浏览器、导航代次、有序 ID 和 index；每次挂起查询/写入之后复查，避免同一 ID 的不同队列位置被当成同一个失败任务。
- 读取歌曲或失败表异常不再逃出播放器回调；报告诊断后，仅当失败项仍归当前任务所有时暂停，不猜测下一首。取消异常继续传播，不误记为数据库故障；写入失败仍沿用已有的记录日志并继续导航规则。
- 抽取小型 `runPlaybackFailureRecovery` 公共异常边界供 Android 调用，5 项测试覆盖成功、不再有效的回调、读取异常、挂起读取期间用户接管、取消传播。连同失败遍历/写入/准备控制，共 24 项通过。
- `/tmp/lmusic-android-failure-recovery-device.log`：独立模拟器 `emulator-5580` 上 15 项既有真实 Media3 测试通过（2.559s）。这是启动选择/优先请求/自然结束/连续坏歌的回归证据，不是 MPlayerPlayback + MediaBrowser + Room 的端到端查询故障注入。
- 尚未覆盖：完全相同列表/index 重装的代次、完整系统暂停与慢数据库交错；不据此勾选全部并发验收。
- `/tmp/lmusic-android-failure-recovery-final.log`：最终源码相关 24 项规则回归通过，Release 构建成功（1m34s）；已覆盖安装小米 `74e1826f`，Success，未操作手机界面。最终只调整 Android 过时提示检查时机与缩进，既有设备测试未再次重跑，不宣称验证了新的查询故障链路。`git diff --check` 通过，未提交或推送。

## 小米反馈：启动首屏队列位置错误（2026-09-14）

- 原因：HistoryRestore 只向 Android 同步列表内容，没有同步历史 index；随后原生默认 index=0 的 Sync 回传覆盖公共队列，界面 rearrange 从列表头展示。等来源 Ready 后 onQueueRestored 才设置正确 index/position。
- 修复：HistoryRestore 使用专用选择同步，在同一原生 setMediaItems 调用中设置列表/index/position，不 prepare/play；同歌曲同重复出现位置补齐列表时保留当前进度，完全相同的列表/index 不重装。普通队列编辑仍走原差量路径。
- `/tmp/lmusic-startup-selection-fix.log`：19 项 HistoryQueueRestorerTest、7 项 PlatformQueueBridgeTest 通过，Release 构建成功（2m15s）。
- `/tmp/lmusic-startup-selection-final-device.log`：最终 15 项真实 Media3 设备测试通过（2.463s），包含无可读取来源时 index=2/position=12000 且 STATE_IDLE、补齐前方条目保留19000ms、相同历史刷新保留进度和优先请求，以及既有自动转场/连续失败回归。此项修复不代表整个长期目标验收完成。
- `/tmp/lmusic-startup-selection-final.log`：最终 Release 构建成功（2m6s），已覆盖安装小米 `74e1826f` 和本任务模拟器 `emulator-5580`，均 Success；未操作小米界面。

## 当前跨平台审计结论（2026-09-14，优先于下方历史记录）

- Desktop 成功重试清除已接入：每次加载为明确的 playbackId 登记写入代次，准备完成后在播放器命令 dispatcher 上观察；命令空闲、当前来源身份匹配、原生正在播放且时间实际前进时清除。仅暂停准备不清除；新加载/stop 取消旧观察。异步 error 归属与跳过尚未接入。
- `/tmp/lmusic-vlc-successful-retry.log`：8 项真实 VlcPlaybackNativeTest 通过，无失败/跳过；新增测试先保存当前歌曲及另一来源同 raw ID 的失败，暂停准备 750ms 后仍保留，真正播放后只清当前项。Desktop 编译及 Android Release 构建成功，39s。此证据使用内存错误仓库，不替代磁盘持久化或迟到原生回调的完整测试。

- Desktop 加载异常持久化第一步已落地：provideVLCPlayback 注入生产 PlaybackFailureRepository，playItem 使用命令明确持有的 LAudio.playbackId 记录安全错误类别；来源未 Ready 不记录，CancellationException 直接传播，写库失败不替换原始加载异常。没有把通用 VLC 异步 error 回调猜测归属到当前歌曲。
- `/tmp/lmusic-vlc-owned-load-failure-green.log`：7 项真实 VlcPlaybackNativeTest 全部通过、0 跳过；包含加载缺文件的来源限定记录、写库异常保留原始异常、暂停取消不留错误。使用内存错误仓库验证适配器写入，不宣称已覆盖 Desktop 磁盘重启。Desktop composeApp 编译及 Android Release 构建成功，36s。
- 本步仍未完成：VLC 异步错误归属、真正播放成功后的清除、沿方向有界跳过、iOS 对应接入。旧的“Desktop 完全未接入”描述仅对之前状态成立；不得把本步当作跨平台错误生命周期已完成。

- `/tmp/lmusic-post-native-full-regression.log`：当前源码完整播放器 JVM 回归 199 项、领域层 JVM 回归 83 项，合计 282 项，失败/错误/跳过均为 0。显式启用生成目录中的 VLC 原生资源与音频夹具；iOS Simulator 播放器编译通过，总耗时 1m27s。
- 明确未完成：VLCPlayback.error 目前只 emitError，AbstractPlayback 的引擎 Error 分支也只 emitError（iOS 原事件会进入此分支）。它们没有接入 Android 已使用的失败原因持久化、成功清除和沿方向有界跳过。因此 Android 失败测试不能证明 Desktop/iOS 一致，应优先补公共/平台接入及真实 VLC 错误回归。
- 校正一次初步判断：VLC finished 调用 skipToNext 不足以证明单曲循环错误，因为公共 skipToNext 已有 SINGLE_LOOP 分支。不得把此推测当成已复现 bug，也不因此随意改动完成回调。
- 仍待收敛的其他明确缺口：历史队列事件排队过时、同曲同槽重新装载代次；Android 完整 MediaBrowser/数据库异常与暂停竞争；完整来源状态/UI 矩阵与 #17 文件操作验收。下面的历史记录含已被后续修复替代的状态，最终必须按当前代码和最新证据逐项审核，不能累计通过数即宣告完成。

## Android 原生队列测试补充（2026-09-14）

- 真实 MP3/FLAC 两首队列冷启动：重启前 MP3 为 PAUSED 21109ms、active item 1；force-stop 后正常启动仍为 PAUSED 21109ms、active item 1、队列 2、error=null。已查看 `/tmp/lmusic-real-audio-restored.png`：Super Ball/Clockwise 标题、两首封面和 00:21/03:22 进度正常。这是实际 Release 的双曲恢复证据，不覆盖未就绪来源。
- 新增 `QueuePlaybackTest.recorderRejectsLatePositionFromPreviousSourceAndThenSavesNewSelection`，实际启动 PlaybackHistoryImpl 的录制协程并挂起旧原生进度读取；切换到相同 raw ID、不同来源后释放 42000ms 旧结果，确认不写入，并在后续采样正确保存新来源 7000ms。`/tmp/lmusic-real-history-recorder.log`：22 项 QueuePlaybackTest 全部通过、Release 构建成功（29s）。队列事件自身排队过时、同曲同位置重新装载的代次仍需补齐，不据此宣称全部并发边界完成。

- 连续失败计数修复：Timeline 调序仍使旧位置任务失效，但本轮尝试过的统一歌曲 ID 不再随调序清空；显式 begin/cancel 开始新一轮。相同音频的重复队列项无需重复打开已知失败文件。`/tmp/lmusic-shuffle-failure-budget-red.log` 中新增交换中间态测试修复前失败（预期 c 的新位置 0，实际重新选择已失败 a 的位置 2）；修复后 9 项 PlaybackFailureTraversalTest 通过。
- `QueueFailureTraversalDeviceTest` 将生产 QueueControlPlayer、PlaybackFailureTraversal 和真实 ExoPlayer 缺文件错误组合运行，覆盖随机模式前后两个方向。4 首各尝试一次后暂停，不依赖数据库记录来避免重复。连同自动切歌和请求生命周期测试，`/tmp/lmusic-failure-budget-native.log` 共 12 项通过（2.851s）。这里没有实例化 MPlayerPlayback/MediaBrowser 或真实数据库，因此跨进程回调迟到、数据库挂起/异常等完整链路仍需单独验收。
- `/tmp/lmusic-failure-budget-native-repeat.log`：上述两个方向的原生坏歌测试重复 10 轮全部通过。`/tmp/lmusic-shuffle-failure-budget-green.log`：9 项规则测试、设备测试包和 Release 构建成功（2m35s），Release 已安装小米 `74e1826f` 和独立模拟器 `emulator-5580`，均 Success。
- 用户授权从小米复制真实歌曲后，仅读取一首 FLAC（27175976 字节）与一首 MP3（4959076 字节）至 `/tmp/lmusic-xiaomi-audio.Smp6MA`，再复制到模拟器 `/sdcard/Music/LMusic-Xiaomi-Validation`。未移动、修改或删除小米原文件；文件未加入仓库。模拟器 MediaStore ID 为 46/47，可用于后续真实文件回归。
- 最新 Release 在模拟器通过 ACTION_VIEW 打开上述 FLAC 后 PLAYING 3118ms、队列 1；继续打开 MP3 后 PLAYING 5912ms、队列 2、active item 1，均 error=null，crash 缓冲无记录。随后暂停测试播放。此证据覆盖两种真实文件的外部打开与播放，不替代来源匹配路径、完整恢复/删除失败矩阵。

- 新增 `QueueAutomaticTransitionDeviceTest`，在测试私有缓存生成 400ms PCM WAV，让真实 ExoPlayer 解码并自然结束，不手工派发结束回调。覆盖顺序末尾 ENDED、循环回到首曲、顺序末尾兑现优先请求、随机自动切换保留上一首位置；测试后释放播放器并删除自身临时音频。
- `/tmp/lmusic-natural-end-device.log`：新增 4 项及此前 6 项共 10 项设备测试通过，耗时 2.267s。`/tmp/lmusic-natural-end-build.log`：测试包与 Release 构建成功，27s。自然结束的这四条路径已有原生证据，但全坏队列预算、跨进程 MediaSession、随机候选多轮覆盖以及暂停竞争仍未完成，不能整体勾选所有播放模式验收。

- `lplayer` 新增独立 Android 设备测试包 `com.lalilu.lplayer.core.test`，只安装到本任务的 `emulator-5580`。启用测试专用 multidex，不改变 Release 分包策略。
- `QueueControlPlayerDeviceTest` 使用真实 ExoPlayer 和生产 QueueControlPlayer，未用 FakePlayer 替代。首轮 3 项中 1 项失败：删除指定下一首后再加入仍错误兑现旧请求（预期 b、实际 c），见 `/tmp/lmusic-device-test-red.log`。
- 修复在外部删除/替换后清理已不在列表中的请求，清空/整列表设置清除请求；内部随机交换直接调用父类替换，不在两次交换的中间态丢弃请求。
- `/tmp/lmusic-device-test-green.log`：6 项全部通过，包括指定下一首、随机模式保留刚播放歌曲的位置、删除后重加、批量替换、整列表重设、内部随机交换保留两个优先请求。
- `/tmp/lmusic-device-queue-request-fix.log`：设备测试包和 Release 构建成功（1m30s）。Release 覆盖安装到小米 `74e1826f` 和独立模拟器 `emulator-5580` 均返回 Success；未操作小米界面。`git diff --check` 通过。
- 这些是实际 Media3 手动导航和同步 Timeline 回调测试，尚不证明播放自然结束、MediaSession 跨进程命令、App 多次差量替换的整体事务、连续坏歌的方向与预算。上述完整验收项继续保持未完成。
- ht-api-detect 本轮问题：独立 build-logic 的 compileClasspath 返回 CONFIGURATION_NOT_FOUND 且候选为空；deviceTest 域报告 complete 但 InstrumentationRegistry 未命中（实际已编译及运行成功），嵌套 Builder.build 查询被显示为 ExoPlayer.build；切换 main 域后一次 HTTP 500：Missing extension point: com.intellij.javaModuleSystem。未改用户维护的反馈文档，已向用户反馈，依赖源码与编译作为后续证据。

## 身份与历史

- [x] 提供来源名与原始 ID 的可逆统一身份，拒绝非法编码。
- [x] 统一身份和来源限定查询的 8 项单元测试通过。
- [ ] Android MediaItem、服务查询、实际文件读取的运行验证。
- [ ] 持久化队列已采用统一身份并停止旧格式恢复；待存储与冷启动验收。
- [ ] 播放进度携带原生选歌归属，拒绝旧歌曲、重复项和迟到请求的位置。
- [ ] 队列、当前位置、进度由统一记录器完整保存；清除进度不会被后台采样覆盖。
- [ ] 冷启动、部分恢复、失败、暂停拖动和清除进度的真实存储测试。

## 队列与播放

- [ ] 顺序末尾停止、循环首尾衔接、单曲循环、随机惊喜交互的 Android 集成测试。
- [ ] 下一首播放的优先请求接入 App、自动结束、系统控制路径；随机调序不能覆盖请求。
- [x] 方向遍历、连续失败预算和优先请求共 13 项规则测试通过。
- [ ] 连续失败沿原方向继续；暂停、清空、替换、显式选择和系统控制能取消旧任务。
- [ ] 全队列失败只检查一轮，保持暂停，无循环重试。
- [ ] 来源未就绪不记歌曲失败；启动恢复等待和普通切歌跳过语义分开。
- [ ] 数据库错误写入与成功清除顺序一致，不被迟到事件覆盖。

## 错误展示

- [x] 4 项状态分类测试通过：来源未就绪优先灰色、来源恢复不清错误、歌曲缺失为失败、成功后正常。
- [ ] 独立错误表集成测试：来源隔离、扫描保留、成功清除、重启保留。已验证元数据更新、来源隔离、清除及磁盘关闭重开，完整扫描链路仍待验证。
- [ ] 实际异常分类成安全的用户文案，不展示凭据、原始路径和服务器异常内容。
- [ ] 队列、曲库、搜索、专辑、歌手、歌单、首页和历史歌曲展示错误原因与重试。
- [ ] 窄屏/宽屏/长原因的截图与交互验证，保留去线留白、粗字体、无阴影设计。
- [ ] 失败到成功、失败到来源未就绪、来源恢复后仍保留失败的 UI 回归。

## 最终验证

- [ ] 固定源码后运行相关公共、数据库、UI、Android 集成测试。
- [ ] 其他已支持平台编译和受影响播放逻辑回归；不得以 Android 通过证明其他平台。
- [ ] Release 构建成功，安装小米 74e1826f，不擅自操作用户手机界面。
- [ ] 独立模拟器真实坏文件、慢来源、连续切歌、重试成功及冷启动测试。
- [ ] 逐项复查以上证据后才标记目标完成，不提交或推送未经用户要求的变更。

当前验证中：固定源码构建日志 `/tmp/lmusic-player-fixed-batch.log`；错误表集成测试日志 `/tmp/lmusic-failure-database-test.log`。生产接入尚有未完成项，不作为最终验收。

## 本批验证结果

### 小队列随机历史与 Android 自动转场（继续验收）

- SurpriseQueueOrder 的随机候选排除紧邻当前的上一首位置（两首队列例外：只有一个其他位置，交换自身不会移动历史）。新增测试覆盖长度 2–8、每个起点、21 个随机种子，验证转场后再次准备下一首仍保留当前与刚播放过的歌曲，并保持成员不变。
- Android 优先请求在 AUTO 后使用已切到的当前位置，保持旧当前位于其后；已自动选中的请求不重置进度。自动替换当前位置后显式准备未来下一首，避免同索引没有 SEEK 回调时漏掉准备。
- `/tmp/lmusic-small-shuffle-history.log`：5 项随机顺序测试、21 项公共队列测试通过，Release 构建成功（1m31s）。完整回归与最后一处自动准备调整见 `/tmp/lmusic-shuffle-auto-regression.log`。
- `/tmp/lmusic-shuffle-auto-regression.log`：完整播放器 JVM 196 项通过，无失败/错误/跳过，Release 构建成功（2m1s）。最终 APK 已安装小米 `74e1826f` 与独立模拟器 `emulator-5580`，均 Success；安装不等于 Android 自动转场已完成验收。
- 这些测试不执行 Android QueueControlPlayer。下一步优先增加独立模拟器的真实 Media3 测试：AUTO/SEEK/Timeline 回调、优先请求移除及替换、连续坏歌的有界跳过。尤其要检查内部随机重排是否错误重置失败遍历代次，不能把公共规则测试当成原生回调证据。

### 优先播放请求的生命周期（公共播放器）

- AbstractPlayback 在稳定队列编辑后即时丢弃已移除歌曲的优先请求；移除再加回不会复活旧请求。替换队列、清空队列与清理请求在同一个 queueEditMutex 内执行，避免清理和实际换队列之间插入新请求。
- `/tmp/lmusic-request-lifecycle-common.log`：QueuePlaybackTest 21 项、PlayNextRequestsTest 5 项通过，Release 构建成功（2m40s），小米覆盖安装 Success。
- `/tmp/lmusic-request-lifecycle-native.log`：完整播放器 JVM 测试通过，包含真实 VLC 测试且无跳过；iOS Simulator 编译通过（51s）。验证修改锁范围后没有在这些测试场景中卡住，不表示已覆盖全部并发交错。
- Android QueueControlPlayer 独立持有请求队列，尚未接入这一稳定编辑后的清理。不能直接按每次原生 Timeline 变化清理：随机模式两次 replace 之间会产生临时移除，可能错误丢弃仍有效的请求。
- 随机自动转场仍需修复：目前请求在 AUTO 后直接跳位置，SEEK 回调还会再次 tryMoveNext。小队列的 candidateIndex 也可能选中刚播放过的相邻歌曲。需结合真实原生回调验证最终顺序，不能以公共 DevicePlayback 的排序测试代替。

### 历史、歌单与详情的限定 ID 链路

- Android 历史监听、VLC 历史写入、历史列表/详情查询已使用 playbackId；历史播放、歌曲详情、搜索、曲库、专辑和歌手页面的播放动作均传限定 ID。列表播放按请求顺序查询，保留重复位置；被点选歌曲缺失时取消本次动作，不改播其他歌曲。
- 收藏/添加到歌单、移除、拖动排序保存、歌单页面查询已使用限定 ID。歌单 UI 合并同一 playbackId 的重复项；不同来源相同原始 ID 使用不同的界面 key。空或不存在的歌单不再回退查询整个曲库。跳转当前歌曲及历史统计排序同步使用限定 ID。
- 数据库版本 5 仅升级历史和歌单引用，保留记录、名称和顺序；仅唯一候选会转换。无法确定的旧引用保留为不可解析的 `unresolved:` 标识，不猜测来源，也避免旧原始 ID 恰好长得像另一个限定 ID 时误选。`SourceQualifiedReferenceMigrationTest` 覆盖缺失、歧义、ID 形似及顺序/重复保留。
- `/tmp/lmusic-qualified-user-journeys.log`：构建通过（2m31s），搜索 9 项、播放选择 3 项、真实数据库/升级 3 项通过。`/tmp/lmusic-qualified-ui-identities.log`：补齐界面 key 后 Release 构建通过（2m26s），小米和独立模拟器安装 Success。
- `/tmp/lmusic-qualified-journeys-full-regression.log`：播放器与领域层全量合计 275 项通过，0 失败、0 错误、0 跳过；iOS Simulator 播放器目标编译通过（1m12s）。
- 独立模拟器从版本 4 升级后队列保持两首及 PAUSED 13677ms。历史列表保留旧记录，点击可播放（PLAYING，queue=2）；从历史长按进入详情能看到原 MediaStore URI `content://media/external/audio/media/18`，详情播放成功。截图 `/tmp/lmusic-qualified-history-screen.png` 已查看。
- 真正通过选择菜单添加到“我喜欢”，收藏页面显示该歌曲，点击成功播放（PLAYING 27151ms，queue=1）。这验证了实际 UI 写入→数据库→UI 查询→播放，不仅是模拟 Repository。完整多来源同原始 ID 的界面运行、双歌曲拖动和其他遗留事项仍需后续验收。
- 收藏移除也完成真实 UI 验证：全选测试收藏并按删除按钮的长按要求执行，关闭菜单后只剩歌单标题，无歌曲行；没有回退显示整个曲库。仅移除了本轮在独立模拟器中添加的收藏，原外部音频仍存在。整个本批运行 crash 缓冲无记录；未操作小米界面。

### 数据库歌曲身份贯通（进行中）

- 精确读取增加 `AudioRepository.getAudiosByPlaybackIds`。真实 DAO 直接按 `song_id` 主键查询歌曲，不再通过无索引的 `raw_id` 或装载专辑/歌手关系；`getPlaybackSlots`、单曲精确查询和队列元数据刷新已接入。`/tmp/lmusic-qualified-query-entrypoint.log`：5 项身份单测和 1 项真实数据库测试通过，Release 成功（1m59s）。
- `/tmp/lmusic-qualified-queue-refresh.log`：4 项队列元数据刷新测试通过，Release 成功（1m20s），已覆盖安装小米 `74e1826f`（Success）。队列刷新测试额外禁止 raw-ID 查询，复验日志 `/tmp/lmusic-qualified-queue-refresh-contract.log`。
- 剩余身份链路须成组迁移：历史写入（Android AnalyticsListener、VLC tracker）、历史列表查询和点击播放；歌单/收藏写入、SearchAudiosUseCase 的指定列表查询、列表删除及播放；SongDetail 路由/VM/播放动作；启动恢复与系统恢复查询。当前改动未声称这些链路已全部完成。

- 真实快照事务的红灯证据：`/tmp/lmusic-source-qualified-db-red.log`，第二个来源写入原始 ID `42` 时抛出 `Audio id is already owned by another source: 42`。
- `LAudioEntity` 现以来源限定的 `playbackId`（数据库 `song_id`）作主键，原始 ID 独立存入 `raw_id`。歌曲与专辑/歌手/流派的关系均使用限定 ID，来源扫描及清理已通过真实数据库测试。原始 ID 单条查询有多个结果时返回 null，不任取一个。
- 开发设备数据库升级到版本 4，使用 Kotlin 的 MediaKey 编码重建身份和关系；不是旧播放历史格式兼容方案，也不清空现有用户数据。补有直接运行 MIGRATION_3_4 的 Unicode 来源名测试。
- 播放队列的精确查询现可获得跨来源的全部原始 ID 候选，再按 MediaKey 匹配；歌单/历史以及部分 PlayById 调用仍传原始 ID，尚未全面切换到限定 ID。不得据此标记所有身份入口完成。
- `/tmp/lmusic-source-qualified-db-regression.log`：34 项数据库回归通过，Release 构建成功（2m40s）。`/tmp/lmusic-source-qualified-db-exact-lookup.log`：额外验证真实 AudioRepositoryImpl 的跨来源顺序、重复位置，以及旧原始 ID 恰好等于另一首新限定 ID 的升级关系重建，2 项通过（19s）。
- 独立模拟器从已有版本 3 数据升级后，原两首队列恢复为 index=1、PAUSED 0ms，crash 缓冲无记录。仍需历史/歌单入口限定 ID 全面贯通及本批跨平台回归；不标记整体完成。

### 随机优先播放与错误写入补充验证

- 数据库写入异常边界已修复：PlaybackFailureWrites 返回 Result，Android 记录失败日志后继续导航，清除错误失败也不会从回调抛出；CancellationException 保持传播。7 项写入测试通过，包含挂起写入、清除失败后重试和取消传播。仅保证写入边界，查询数据库发生异常时的整体回调处理仍需审查。
- `/tmp/lmusic-storage-failure-boundary.log`：Release 构建成功（1m6s），小米 `74e1826f` 与独立模拟器 `emulator-5580` 安装 Success。模拟器启动恢复两首队列、index=1、PAUSED 0ms，crash 缓冲无记录；这不是故障注入验收。
- `/tmp/lmusic-storage-boundary-platform-regression.log`：播放器完整 JVM 回归 189 项通过，0 失败、0 错误、0 跳过，包含启用真实 VLC 资源的测试；`:lplayer:compileKotlinIosSimulatorArm64` 编译通过，总耗时 59s。iOS 编译证据不替代 iOS 运行验收。

- `/tmp/lmusic-requested-surprise-order.log`：Release 构建成功；QueuePlaybackTest 19 项、PlayNextRequestsTest 4 项通过。公共播放器和 Android 手动下一首会将指定歌曲放到随机模式的前一个播放位置；公共测试验证展示顺序为“指定歌曲、之前播放歌曲”且不新增重复项。Android 自动播完转场仍需单独修复和运行验证，不能据此声称随机优先请求已完整验收。
- Release 已覆盖安装到小米 `74e1826f`，返回 Success，未操作手机界面。
- `/tmp/lmusic-failure-write-races.log`：构建成功，PlaybackFailureWritesTest 新增写入挂起后成功事件、数据库写入异常后后续事件继续执行的测试。前者证明旧错误写入完成后，新成功事件会清除它；后者只证明写入锁能释放，不代表 Android 播放错误回调已经能容忍数据库异常。Android 回调目前直接等待写库，该异常边界仍需处理。

### 接入复查发现，尚待修复

- Android 错误写入已接入按歌曲登记代次与串行提交，3 项迟到事件规则测试通过；仍需验证写入挂起期间的新事件及真实播放器回调。
- Android 服务和 App 已共享导航尝试，通知栏 next/previous 及其 MediaItem 方法会同步方向；不再在刚进入 isPlaying 时清空失败预算或方向，自然播完转场才重置为前进。仍需真实通知栏连续失败验证。
- 服务端 pause、stop、setPlayWhenReady(false) 已取消共享失败导航代次；仍需挂起错误处理时的原生暂停竞态验收。独立待准备任务已接入 pause/stop 标记，暂停只允许静默 prepare，stop 禁止旧请求 prepare；再次播放刷新标记，并在主线程应用时读取最新意图。直接 setPlayWhenReady(false) 对独立准备任务的区分（用户操作与恢复流程自身设置）仍需完善，完整慢来源系统控制运行验收仍未完成。
- Android timeline 变化已按完整有序播放 ID 列表使旧失败任务失效；元数据刷新不清尝试预算。仍需原生回调顺序验证，并检查完全相同列表被重新装载时的选歌代次。
- 顺序模式末尾已添加 STATE_ENDED 回调兑现优先请求，并检查当前状态和播放意图，避免过时回调恢复已暂停的播放；仍需真实 Media3 集成验证。
- 首页推荐与历史专用歌曲卡片均已接入红色错误原因；历史卡片和完整页面仍缺真实失败运行验证。
- `l_audio` 主键和数据层批量查询仍基于原始 ID。统一播放 ID 已避免误选其他来源，但真正容纳不同来源同原始 ID 的数据库记录还未贯通，不能用 FakeRepository 的同 ID 测试证明实际数据库已支持。

以上是当前代码确认的缺口，需修复并补回归；不得在最终验收时忽略。

- `/tmp/lmusic-player-fixed-batch.log`：BUILD SUCCESSFUL，4m46s；本批 17 项规则测试通过，Desktop 编译与 Android Release 构建通过。
- `/tmp/lmusic-failure-database-test.log`：BUILD SUCCESSFUL；1 项真实 Room 内存数据库测试通过，验证来源隔离、歌曲信息更新保留错误、成功清除指定记录。不覆盖磁盘数据库关闭重开。
- Release 已通过 `adb -s 74e1826f install -r` 覆盖安装，返回 Success。未操作用户手机界面，也未把安装成功当作运行验收。
- 优先请求已接入公共播放器和 Android 服务。Android 顺序播放末尾已接入但未完成运行验收，队列替换后的清理及随机视觉位置仍未完成。错误已区分缺文件、权限、网络和未知；更细的解码类别与运行验证仍未完成。系统控制方向、完整 UI、历史保存和多平台回归仍需推进。
- `/tmp/lmusic-home-failure-status.log`：Release 构建成功，MediaContentResolverTest 10 项通过，覆盖“来源已就绪但歌曲缺失”不会被误当成来源加载失败；已安装小米，未操作手机 UI。
- `/tmp/lmusic-sequential-completion.log`：公共 QueuePlaybackTest 17 项通过，新增顺序末尾暂停、循环回到开头和顺序末尾优先请求仅消费一次的测试；Release 构建成功。此测试替换了音频设备，不证明 Android 原生自动结束路径。
- `/tmp/lmusic-failure-ui.log`：SourceRecoveryUiTest 5 项通过。新增真实 Compose 卡片测试验证失败可点、来源未就绪不可点且隐藏失败提示、来源恢复保留错误、错误清除后正常。检查了 `/tmp/lmusic-failure-ui/failed-song-phone.png`，窄屏原因换行完整。测试由手动更新状态驱动，不证明原生播放成功会正确清除数据库错误，也不覆盖历史卡片或全部页面。
- `/tmp/lmusic-failure-queue-generation.log`：PlaybackFailureTraversalTest 7 项通过，新增队列删除、重排、重复项数量变化使旧位置失效，以及相同列表刷新不重置预算的验证。未替代原生回调集成测试。
- 冷启动发现恢复器将相同队列的原生 Sync 回传误判为取消。`/tmp/lmusic-restore-sync-red.log` 中新回归测试修复前失败（预期交付 8070ms，实际未交付）；修复后 `/tmp/lmusic-restore-sync-green.log` 的 HistoryQueueRestorerTest 19 项通过。仅在顺序、来源身份及选中位置一致时允许 Sync；仍需验证多曲目非零索引的原生恢复。
- 修复安装到独立模拟器 emulator-5580 后，新播放并暂停在 10698ms；force-stop 再启动恢复为 PAUSED 10698ms，队列 1 首，证明新统一 ID 格式的单曲冷启动恢复可用。此前失败运行中的 8070ms 未保留，首次安装修复包为 0ms；进度的统一保存仍是未完成项。
- 多曲目运行验证：独立模拟器两首队列，原生 active item id=1、PAUSED 8751ms；force-stop 后重新启动仍为两首、active item id=1、PAUSED 8751ms，crash 缓冲无记录。`/tmp/lmusic-history-duplicate-index.log` 的恢复测试将即时 Sync 场景扩展为 [a,b,a] 的 index=2，验证重复项位置不折叠。仍不代表慢来源、失败或用户接管期间全部竞态已验收。
- `/tmp/lmusic-failure-disk-reopen.log`：PlaybackFailurePersistenceTest 通过。使用生产数据库构建方法创建唯一命名的磁盘数据库；保存两个来源同原始 ID 的失败记录，关闭重开后均保留，清除一条后再次重开只保留另一条。测试清理仅删除自身数据库及 journal/WAL 文件。该结果证明错误表持久化，不证明歌曲主表能容纳同原始 ID 的跨来源记录。
- 真实缺文件与重试：在独立模拟器 emulator-5580 临时移走自建合成音频 `Download/LMusic-Cover-Validation-45s.mp3`（MediaStore 18），冷启动并播放后为 ERROR/Source error，曲库显示“播放失败 · 找不到音频文件 · 点击重试”。已查看真实截图 `/tmp/lmusic-real-missing-file.png`，红色提示完整。文件在 finally 中恢复，361803 字节不变；点击失败行后 PLAYING、position=14996ms、error=null，UI 错误提示消失。随后暂停测试播放。该场景覆盖 MediaStore 文件缺失→重新可读→手动重试成功；不覆盖解码错误、网络失败或连续坏歌的全部方向。
- 完整 JVM 回归：`/tmp/lmusic-domain-full-regression.log` 领域层 81 项通过。播放器初次运行因 Rococoa 测试使用旧资源路径失败，已改为显式 LMUSIC_NATIVE_RESOURCES 或当前构建资源目录。VLC 原生测试启用后发现 composeApp/build/compose/tmp/prepareAppResources/vlc 为空；改用已存在的源码资产 lplayer/src/jvmMain/assets/macos 后，`/tmp/lmusic-player-native-assets.log` 构建成功，原生测试不再跳过。此证据不证明 Desktop 打包资源完整，生成目录缺失 VLC 的原因仍需跟进。
- 生成资源复核：配置指向完整的源码资产，执行 `:composeApp:prepareAppResources --rerun-tasks` 后 VLC 恢复到生成目录。使用该生成目录运行 `/tmp/lmusic-generated-native-regression.log`，173 项测试全部通过、0 跳过，耗时 2m9s。由此确认此前是临时产物缺失，无须修改打包配置；这不替代最终安装包内容检查。运行中一度原生初始化较慢，线程采样位于 libvlc_new，之后自行完成，未终止或重启测试。
- Android 进度采样已接入 HistoryPositionProvider，在主线程同时检查原生完整有序播放 ID、选中索引与公共队列快照，再读取原生 position。`HistoryPositionSelectionTest` 5 项通过（包括跨来源、重复位置和中间队列），但相同列表/同一位置重新加载的命令代次及保存时原子性仍未完成，不标记完整进度归属验收通过。
- Android onPlayerError 不再为未就绪来源启动无限等待；普通报错统一沿失败方向遍历，未就绪来源不写歌曲失败记录。启动 onQueueRestored 的等待准备仍保留。待真实慢来源中途失效及连续跳过运行验证。
- 历史已合并成一个序列化记录（统一播放 ID 列表、选中索引、position），生产 restore 一次读取完整记录，进度采样一次写入完整记录；录制器各流串行写入。选中歌曲/重复位置改变时进度归零，同一位置因前方插入而移动则保留。`/tmp/lmusic-unified-history-record.log` 构建与相关测试通过，StoredPlaybackQueueTest 7 项通过；独立模拟器两首队列 index=1、PAUSED 11053ms，结束进程再启动仍为 index=1、PAUSED 11053ms。清除进度入口已指向新记录，但防后台覆盖、设置测试真实入口覆盖及同曲目重载代次仍未完成。
- 清除进度防覆盖已接入独立 reset 请求，只有读取有效恢复队列时才消费并将新记录进度置零。`/tmp/lmusic-clear-history-request.log` 构建通过，HistoryPositionResetSettingsTest 2 项直接使用真实设置贡献与生产存储，覆盖后台写入及空记录不消费请求。独立模拟器点击真实设置后仍 PLAYING 29141ms、index=1、两首；结束进程重启后 PAUSED 0ms、index=1、两首，crash 缓冲无记录。Release 已安装小米。仍需统一录制器并发写入/同曲目重新装载代次与全量回归。
