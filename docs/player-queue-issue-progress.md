# #14 / #17 合并检查点（2026-09-21）

本页是当前状态摘要；`player-playback-acceptance.md` 和 `player-queue-media-source-rollout.md` 保留逐次开发记录，其中旧的“未提交”“未完成”以本页及较新的记录为准。

## 本轮：对账审计与收口（2026-09-21）

对 #14 / #17 声称“已实现”的 26 个子项逐条回代码核验，结论是**代码主体确实落地、但正文有 8 处表述与实现不符**。逐条证据见 `player-playback-acceptance.md` 顶部的本轮记录。这里只保留两类需要长期记住的内容：**已修正的**与**仍不成立的**。

### 已修正（本轮改代码）

- iOS 播放中途失败原先固定向前跳（`skipToNext` 写死 `Forward`），与 Android 的 traversal 语义不一致；现改为沿“加载当前项时的方向”继续，守卫抽成公共规则 `stalledFailureNavigationDirection`。
- `PlaybackFailureReason.Decode` 原先不可达（分类器不产出它），“音频解码失败，文件可能已损坏”永远不显示；现按引擎自造的 `AVAudioPlayerDecodeError` 标记归类。

### 表述已纠正、但请勿再按旧说法理解

以下 6 条在 issue 正文里的写法与代码不符，**不要用它们当验收依据**：

- “电脑版 `prepareVlcMedia` 改为只发起加载”——该函数一字未改，仍阻塞 30s + 5s，且仍在历史恢复路径上（`restoreInternal` → `playItem` → `prepareVlcMedia`）。真正只发起加载的是新增的 `startVlcMedia`，选曲路径用它。这是**有意的**：历史恢复不在队列编辑边界内。
- “SHA-256 计算在 commonMain”——SHA-256 只在 nonWebMain；commonMain 里是 **MD5**，用于稳定歌曲 ID。两者被混为一谈。
- “临时 `.part` 复制”——全仓没有 `.part`。实际是 `workingDirectory/import-<randomToken><源扩展名>`，索引暂存才是 `.tmp`。
- “不申请外部文件写权限”——清单里确有 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion=29`），但它早于本特性（`4ebe9e8d`，2026-01-02）；沙箱根是 `filesDir`，导入路径确实不需要它。
- “UI 不直接订阅 MediaSource”——有反例：`MediaCoverRequest` 在 `@Composable` 里直接 `collectAsState()`，`SourceCard` 直连 `MediaSource.snapshot`。聚合架构本身成立，但这句作为绝对陈述不成立。
- “解码错误都会抛出可分类异常”——解码错误不抛出，只写状态并发事件（靠状态轮询上账）。

另有一项是**有意未做**，不是遗漏：iOS 重复投递同一打开事件没有加守卫。核验后确认 iOS 只有单一 `WindowGroup` + 单个 `.onOpenURL`，无多场景声明，重投递没有证据支撑；重复导入在数据层已幂等（内容摘要去重 + `selectOrInsert` 复用），因此没有加投机性守卫。

**结案一项**：长期挂着的「`MediaCoverRequest` fetcher 异常复核」已查明**不是 bug**。`EngineInterceptor.fetch` 会按下标循环找工厂，全部返回 null 后抛 `Unable to create a fetcher that supports`；我们的工厂确实被调用并返回了 `LAudioFetcher`，但该曲**没有内嵌封面**时 `fetch()` 按 Coil 约定返回 null 表示"交给下一个工厂"，而没有其他工厂认识 `MediaCoverRequest`，于是走到底抛出该文案。注册是生效的，界面按占位降级（截图已确认），故不改代码。

### 身份与历史（#14 核心）

- 数据源独立产生 Snapshot、独立入库；读取能力与扫描状态分开，UI 通过 Repository 获取状态。
- 播放身份统一为可逆的来源限定 `playbackId`；原始 ID 保留在源内使用。数据库主键、关系、播放动作、歌单及历史引用同步调整。
- 历史列表、当前槽位和进度作为一个记录保存；保留列表顺序和重复槽位。数据库已有歌曲立即展示，未就绪歌曲置灰并禁用卡片交互。
- **历史身份里已经带了 `sourceName`**，因此“历史 current 缺失时无法定位来源、只能等所有来源进终态”这条旧边界已经解决：`HistoryQueueResolution` 按来源名解析，`AbstractPlayback` 只对相关来源 `observeHistoryRestoreSettled`。
- 全局恢复提示倒数 15 秒、可手动关闭；超时只隐藏提示，不取消来源任务、不删除歌曲。
- 失败原因单独持久化；来源未就绪优先置灰，歌曲播放失败显示红色原因并允许重试，成功播放后清除失败记录。失败跳过沿方向进行且有遍历上限（两端各 60s 总预算）。

### 外部打开与 Sandbox（#17 核心）

- Android 的 ACTION_VIEW、冷/热启动与 singleTask 入口已接入；优先匹配 MediaStore/已有来源，无法确认时导入 Sandbox，不申请外部写权限。
- Sandbox 公共接口在 commonMain，文件操作抽象在 nonWebMain（FileKit 的路径/遍历/移动/删除 API 只在它的 nonWebMain 提供）；Android/iOS 提供平台扩展。
- 内容去重（SHA-256）、稳定身份（MD5 of 相对路径）、临时复制、MagicNumber/Taglib 校验、持久化索引与原子替换已实现；独立 Screen 可浏览、重命名和删除。
- 删除先隔离到 `.pending-deletions` 并写 journal，确认数据库与队列移除后才删私有副本；失败恢复文件/索引/快照，队列回滚有本次操作归属保护，恢复后保持暂停。进程中断靠启动时的 journal 恢复。
- 重命名等待数据库与队列路径刷新。
- **JVM/Desktop 没有 Sandbox 实现**（只有 Android 与 iOS 两个子类），这是文档认可的后续项，不是死代码。

### 仍需推进

- **验收（需设备）**：真机（小米 / iPhone / iPad）运行验收；Desktop 与 Web 冒烟。iOS 自动化只有播放器引擎的 `iosTest`，`lmedia-core` 没有 `iosTest`。
- **测试缺口**：应用层 `SandboxFileOperationsImpl`（文件 + 数据库 + 队列联合事务）、`ExternalAudioOpenCoordinator`、`MainActivity` 均无用例（都依赖全局 `LPlayer.instance`，补测需要先有注入缝）；iOS `audioPlayerDecodeErrorDidOccur` 路径无用例；真实文件系统失败 + Room + MediaBrowser 的整条故障注入未做。
- **结构性**：失败跳过整体仍位于队列编辑锁内（有 60s 预算兜底），解耦为“投递新命令”未做；MediaBrowser / 通知栏 / Room 慢查询的完整竞争矩阵未做；MusicKit 迟到异步错误的 storeID / 加载代次归属未做（建议记录不改）。
- **环境问题**：`./gradlew :lplayer:connectedAndroidDeviceTest` 在本环境报告 `Starting 0 tests`（测试 APK 能正常生成），原因未定位。绕过方式见 `player-playback-acceptance.md` 本轮记录。

### 验证证据（本轮）

- `:lmedia:lmedia-core:jvmTest`：`SandboxSourceFileOperationsTest` 16 项通过（原 13）；`:lplayer:jvmTest` 292 项通过（原 290），失败 0、跳过 6。
- iOS 原生（iPhone 17 Pro / iOS 26.5 模拟器）：7 类 74 项、`testFailed` 0（原 71）。
- Android 设备测试（API 36 模拟器、真实 Media3）：5 个类 22 项全部通过。
- **Android 外部打开端到端**（真实链路、模拟器执行）：真实 MP3 经 `am start -a android.intent.action.VIEW -d file://… -t audio/mpeg` 冷启动 → 导入应用私有沙箱（外部原件未动）→ 来源限定身份入库 → 入队 → `media_session` 报 `PLAYING(3), position=10458, error=null`，播放页截图显示该曲与 00:12/00:30 进度。

历史上“跳过 0”的说法自 2026-09-18 引入平台守卫后不再成立：Windows 上 `MacOsVlcDiscovererTest`(3) 与 `RococoaTest`(3) 会按约定计为 skipped，正确基线是 290/0/6。

## 上一轮检查点（2026-09-14）保留要点

- 审查分支 `codex/14-17-player-queue-sandbox`，按用户要求暂停扩展失败场景，Desktop/iOS 运行验收暂缓。
- 该轮只做了阶段交付，不代表全部边界验收通过；#14、#17 当时均保持打开。
- `docs/ht-api-detect-feedback.md` 是用户独立记录，不纳入提交。
