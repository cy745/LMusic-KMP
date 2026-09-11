# 播放队列、多数据源恢复与 Sandbox 分阶段推进

关联需求：[#14](https://github.com/cy745/LMusic-KMP/issues/14)、[#17](https://github.com/cy745/LMusic-KMP/issues/17)。

## 当前阶段性提交状态（2026-09-11）

本次按用户要求保存四阶段当前实现，不代表 #14、#17 已完成或全部验收通过。下文按迭代保留历史记录，最新状态以本节为准。

- 最新选定的 lplayer JVM 测试 130 项通过，0 失败、0 跳过；Android Release 构建成功，已安装小米 74e1826f 与 emulator-5556。最终日志 `/tmp/lmusic-history-position-final.log`。Desktop 编译、iOS Simulator 与 Wasm 编译已有通过记录，不等同于各平台完整运行验收。
- Android 最新测试暂停为 15708ms，普通冷启动恢复为 9812ms，存在尚未解释的位置偏差。证据 `/tmp/lmusic-position-guard-paused.txt` 与 `/tmp/lmusic-position-guard-restored.txt`。随后发现 emulator-5556 正被其他任务使用并显示其他应用，已停止操作；需隔离重测，既不能归因于环境而忽略偏差，也不能据此认定代码根因。
- 新建隔离 AVD `codex_lmusic_history_isolated` 因磁盘空间不足未能启动，尚无隔离复测结果。
- 剩余验收重点：历史身份与进度写入一致性、跨平台原生命令完成与迟到回调、数据源就绪后的封面/歌词重试、iOS 外部文件运行验证，以及 Sandbox 失败重试和恢复冲突处理。保留安全导入文件以重试数据库提交，不以删除文件替代恢复。

## 已确认的交互约束

随机模式沿用 Android `QueueControlPlayer` 的反向环形队列：随机候选交换到当前项前一个槽位，随后沿索引 -1 前进。展示层从当前项开始旋转队列，新歌出现在顶部，旧当前项紧随其后。沿 +1 返回之前的歌曲。保留原有避开当前附近区域的候选规则；不全量洗牌，也不新增无限增长的重复歌曲历史。

“上一条”描述视觉位置，不等于 Media3 的 `seekToPrevious`：原实现的 `seekToNext` 使用索引 -1，`seekToPrevious` 使用索引 +1。

## 阶段 1：公共队列规则

已实现：

- `MediaKey(sourceName, id)` 用于队列识别、删除、定位和元数据刷新。数据库歌曲 ID 保持原值。
- 删除前方项保留当前歌曲；删除当前项优先选后面的存活项，没有后项则选最后一项；空队列索引固定为 0。
- `removeAt` 删除一个出现位置；`remove(audio)` / `removeAll(keys)` 删除该歌曲的所有出现位置；`removeSource` 批量移除一个来源。
- 插入、移动、替换和元数据更新维护当前项及重复出现位置。
- `QueueAction` 使用 common 实现，通过 `Playback.editQueue` 协调停止、换曲与保留播放意图，替换原有平台空实现 / TODO。
- iOS/Desktop/Web 区分实际加载的歌曲与队列索引，避免新列表索引相同却继续播放旧歌曲。
- Android 和 common 使用同一随机候选及方向规则；非 Android 不再使用独立的全量随机索引表。
- 外部打开及列表点选按 MediaKey 定位。

测试覆盖队列原子编辑、删除/替换/清空、当前项和播放位置保持、同 ID 不同来源、重复出现位置、随机新歌顶部展示与返回历史。播放器边界测试使用真实 AbstractPlayback，仅替换音频设备实现。

本阶段不是 #14 / #17 全部完成：Android 仍保留既有 Timeline 双向同步，历史仍为旧 ID 格式，Sandbox 文件删除尚未接入事务协调。

### 阶段 1 验证记录（2026-09-11）

- `:lplayer:jvmTest --tests 'com.lalilu.lplayer.*'`：60 项通过，包含 22 项新增测试。
- `:lplayer:compileKotlinIosSimulatorArm64`：通过；没有执行 iOS 真机播放验证。
- `:androidApp:assembleRelease`：通过，Release APK 已覆盖安装至 Xiaomi `74e1826f`，ADB 返回 Success。随后设备断开，未完成启动日志与随机交互实机检查。
- 全量 JVM 测试另外包含 3 个 Rococoa 原生库测试，本机出现 UnsatisfiedLinkError；播放器包下测试均通过。
- Web 编译止于既有 `lfont/src/webMain/.../InMemoryFontFileStore.kt:44`，`delete` 返回 Result<ByteArray?> 而接口要求 Result<Unit>，尚未进入 lplayer Web 编译。

### 模拟器 Release 验证（2026-09-11）

设备：`emulator-5554`，Android API 36、ARM64、16KB 内存页。覆盖安装同一 Release APK 成功，冷启动成功。测试使用 12 首自行生成的 180 秒静音 MP3，带编号、歌手和专辑标签，无嵌入封面；保存在模拟器 `/sdcard/Music/LMusicQueueValidation`，验证结束后暂停播放，保留样本供后续回归。

- MediaStore 扫描：媒体库 12 首、可用 12 首。
- 从所有歌曲播放 `Queue Test 04`：MediaSession 为 PLAYING，队列 12 首，进度正常前进。
- 长按进度条右侧选择随机模式，连续点击右侧切歌：`04 → 09 → 08`。可见队列顶部依次为 `08 → 09 → 04`，符合新歌向前出现、已播放项依次保留的交互。
- 点击左侧返回：当前歌曲由 `08` 回到 `09`。
- 暂停后点选队列中的 `10`：成功切换并开始播放，队列仍为 12 首。
- ACTION_VIEW 打开 `content://media/external/audio/media/122`：日志记录 `Matched ... by SourceLocator`，成功播放 `01`，队列保持 12 首，没有重复插入。
- 暂停/继续：位置从 20832ms 继续到 20833ms，没有重置或换曲。
- 本轮进程 crash buffer 为空，MediaSession `error=null`。无封面样本产生预期的 Picture not found 日志；本轮未验证实际封面加载、完整历史重启恢复或 Sandbox 删除事务。

证据保存在 `/tmp/lmusic-queue-validation.HfycRa`：`random-before.png`、`random-after-first.png`、`random-after-second.png`、`returned-history.png`、`random-after-second.xml`、`app-logcat.txt`、`media-session-final.txt`。以上是运行时冒烟验证，不替代后续 Android 同步竞争条件测试。

## 阶段 2：Android 同步与命令边界

### Desktop 原生初始化阻塞修复（2026-09-11）

### Desktop 真实适配层验证（2026-09-11，继续）

- 为真实 VLCPlayback 增加可注入的 native player 提供函数、平台控件安装函数与生命周期 scope；媒体源和数据统计改为显式构造依赖。生产 Koin 通过 @Single provideVLCPlayback 注册，仍绑定 Playback / VLCPlayback；生产默认继续加载打包的 VLC 并安装系统控件，没有加入测试模式开关或模拟播放路径。测试使用真实 VLCPlayback、公共队列和 PlaybackHistoryImpl，只替换 Repository/内容素材/存储/系统控件，不启动窗口或有声输出。
- 新适配层测试发现构造期 NPE：VLCPlayback 覆盖 audioRepository 后，父类 init 中的队列元数据订阅读取了尚未初始化的子类属性（`/tmp/lmusic-vlc-adapter-before.log`，2 项失败）。删除重复 override，直接使用父类已保存的构造参数；随后历史 hook 恢复 12 秒、队列不变、真正 play/pause 和暂停 seek 持久化 2 项通过（`/tmp/lmusic-vlc-adapter-position-before.log`）。这不是 Koin/Room/完整 App 冷启动验收。
- 新增停止回归证明旧时间推算确实错误：原生已停止，但 public currentPosition 仍为 2603ms（`/tmp/lmusic-vlc-adapter-stop-before.log`，3 项中 1 失败）。删除 lastTime/lastRecordTime 和 timeChanged 推算，直接读 native time 并将未初始化/负值归零，避免停止/迟到位置事件继续推进历史时间。未将此位置修复描述成所有 playing/finished 事件已具有请求代次。
- ht-api-detect 在索引作用域未找到 Single 和 StatusApi.time；依赖 sources 确认 Single 支持 FUNCTION、time 返回毫秒。KSP 实际生成单例 provider 并绑定 Playback 与 VLCPlayback，是当前注册的编译证据；完整 App 注入/系统控件仍需运行验证。
- 位置修复后真实适配层 3 项通过，Desktop composeApp:compileKotlinJvm 和 Release 构建通过（`/tmp/lmusic-vlc-adapter-final.log`）。再增加真实 VLCPlayback 的挂起数据解析期间暂停接管测试：取消旧选曲、迟到结果不切歌/不播放，随后显式播放原歌曲成功。最终 MP3 全套 127 项通过、无跳过，其中 4 项真实适配层 + 8 项底层 VLC 测试；Release 构建成功（`/tmp/lmusic-vlc-adapter-takeover-final.log`）。
- 本轮历史适配层测试调用真实 onQueueRestored hook，而不是从真实持久化存储冷启动自动恢复；Repository、数据源读取和存储仍为受控 fixture。Koin 完整运行、真实 Room/存储冷启动恢复、平台控件、迟到 playing/finished 回调归属及各平台剩余门禁仍未完成。
- 4 项真实适配层测试以 WAV 素材复跑全部通过、无跳过（`/tmp/lmusic-vlc-adapter-wav.log`）。最终 Release 已覆盖安装小米 74e1826f 与独立 emulator-5556，均 Success；小米仅安装，本轮未新增 Android UI 结论。所有修改仍未提交。

后续真实准备边界（2026-09-11）：VLCPlayback 的普通选曲和历史恢复共用 prepareVlcMedia。native prepare 只接受媒体描述符，不等于音频已加载；现在停止旧输入，以 start-paused 打开并等待原生 PAUSED，确认已有选中音轨后再按实际时长限制恢复位置并等待 seek。失败/取消停止本次输入后向调用方传播，成功后才设置 loadedMediaKey。此处没有把最终 play 的返回值视为已证实播放推进，也未完成 native 事件归属或完整 VLCPlayback/Repository/App 验收。

- 精简插件目录下新增 Bytes/HTTP 测试失败（`/tmp/lmusic-vlc-preparation-final.log`，121 项中 2 失败）；插件自带的最小集合缺少 callback-memory、HTTP/HTTPS 等访问模块。仍使用既有 cy745 fork 的 vlc-setup / VLC 3.0.21，把 shouldIncludeAllVlcFiles 改为 true，并运行插件重新生成预编译资源，没有自行编译 VLC。生成的 macOS 资源目录由约 40MB 增至 182MB，不是 Android APK 体积变化；其他桌面系统的体积和运行行为未验证。
- 完整插件首次运行 122 项中 1 失败（`/tmp/lmusic-vlc-preparation-plugins-final.log`）：损坏 MP3 文本被 ps demux 勉强接受，处于 PAUSED，但没有音轨。增加 audio().track() 非负检查后，122 项全部通过、无跳过，Release 构建成功（`/tmp/lmusic-vlc-audio-ready.log`）。7 项为真实 VLC 测试，覆盖正常文件、负位置、内存音频、缺失/损坏文件、取消受控 HTTP 加载后重试、暂停接管和继续播放。受控 HTTP 取消会主动关闭测试服务器连接，不能据此保证任意不响应服务器的取消延迟。
- ht-api-detect 未在所索引的编译作用域中找到 JVM VLC API；音轨接口依据当前 vlcj 4.11.0 sources 声明及真实 JVM 编译/原生运行验证，不声称技能已证明该符号的 classpath 可达性。
- 新增正常 HTTP 范围请求与恢复播放测试：MP3 全套 123 项通过、无跳过，其中 8 项真实 VLC 测试（`/tmp/lmusic-vlc-http-ready.log`）。WAV 复跑发现测试服务硬编码 audio/mpeg，native 按 MP3 demux 打开 WAV 失败（`/tmp/lmusic-vlc-audio-ready-wav.log`）；修正测试 URL 和 MIME 与素材一致后，8 项原生测试全部通过、无跳过，Release 构建成功（`/tmp/lmusic-vlc-audio-ready-wav-final.log`）。正常网络测试确认 native PAUSED/12 秒恢复位置，再 play 后时间确实推进；没有外网依赖或有声输出。
- HTTPS、其他格式、Windows/Linux 和完整应用集成仍在验收范围内，以上证据不能代替四阶段完成。准备阶段有音轨不代表后续全部帧均可解码，也不承诺损坏任意位置的文件均能在准备阶段发现。
- 最终 MP3 全套重新验证 123 项全部通过、无跳过，Release 构建成功（`/tmp/lmusic-vlc-preparation-verified.log`）；最终 APK 覆盖安装小米 74e1826f 和独立 emulator-5556 均 Success，没有操作小米界面。本轮所有代码与文档仍未提交。后续优先验证真实 VLCPlayback/队列/历史联动及迟到 native 事件归属，不将底层准备测试当作阶段 2/3 已完成。

后续 Desktop 命令接管：VLCPlayback 的 play/pause/stop/skipTo/seekTo 使用同一个 LatestPlaybackCommand；内部实现改为私有 helper，避免同一个命令嵌套调用公开入口时取消自身。公开操作先 claimHistoryPlaybackControl，历史 restoreInternal 则进入控制机制但不自我接管；解析结果返回后检查协程取消，未加载当前媒体时拒绝 seek 到旧媒体。初始化和命令使用同一个 Dispatchers.IO.limitedParallelism(1) 视图，原生时间采样字段增加 volatile 可见性。注意它不是跨 suspend 的 mutex，旧命令退出顺序仍由 LatestPlaybackCommand 保证；不能据此声称所有 native callback 已带 generation/媒体归属校验。

第一版试用 Dispatchers.Main 后，播放器 JVM 模块的真实测试报告 Main 缺失（`/tmp/lmusic-vlc-command-gate.log`）。生产与测试一并改成 IO 单并行度视图，没有通过 setMain 隐藏依赖。ht-api-detect 核实 limitedParallelism 的签名（工具索引为 Android classpath 的 coroutines 1.10.2），另读当前 1.11.0 sources 的顺序/happens-before 和“不是 mutex”约定；JVM 实际编译及原生测试提供本模块的可调用证据。

最终 117 项播放器 JVM 测试全部通过，无跳过，Release 构建通过（`/tmp/lmusic-vlc-command-gate-final.log`）。其中 2 项使用真实 VLC：既有暂停定位恢复，以及新增的“原生已暂停，解析任务挂起，暂停命令取消旧任务，迟到结果不应用，随后显式 play 仍成功”。用 MP3 和 WAV 各运行一次均通过，WAV 证据 `/tmp/lmusic-vlc-command-gate-wav.log`。测试组合的是 LatestPlaybackCommand + 真实 native player，不是完整 VLCPlayback/Koin/Repository/App 实例；真实适配层集成、native event 归属、普通加载 ready 完成边界和队列编辑回滚仍未验收。Latest Release 再次安装到小米 74e1826f 与独立 emulator-5556 均 Success；本轮 jvmMain 变化不改变 Android APK，未新增 Android 界面操作。

- 重跑 `/tmp/lmusic-vlc-recheck.log` 仍在 MediaPlayerFactory 创建实例时失败。JNA 路径跟踪 `/tmp/lmusic-vlc-jna-path.log` 确认加载的是项目内两份 dylib，不是错误发现了别的 VLC。直接 ctypes 探针使用正确插件路径后成功创建/释放实例（`/tmp/lmusic-vlc-ctypes.log`）；首次加载期间采样显示 dyld 的 fcntl/加载工作，后来正常结束，没有把观察超时当作进程终止。
- 根因：vlcj BaseNativeDiscoveryStrategy.onSetPluginPath 已把 `%s/plugins` 展开并检查存在，传给 setPluginPath 的是完整插件目录。MacOsVlcDiscoverer 又拼 `/plugins`，使环境变量指向 `plugins/plugins`。现在直接使用回调路径。新增 3 项 JVM 测试验证准确目录、目录不存在、环境变量设置失败；首项在修复前明确失败（`/tmp/lmusic-vlc-plugin-path-before.log`）。通过注入 setter 测试真实父类展开行为，不在单元测试中修改进程环境。ht-api-detect 按技能查询但未索引该 JVM 类，以当前 vlcj 4.11.0 sources 声明和实际 JVM 编译/测试核实，未声称技能验证成功。
- 修复后先用旧实验产物通过原生测试，然后把整个忽略的生成目录移到 `/tmp/lmusic-vlc-generated-backup.R8yRSe/vlc` 保存（可恢复，未删除），执行 `:composeApp:vlcSetup` 重新生成。生产配置保持 shouldIncludeAllVlcFiles=false，干净目录 plugins 16 个文件、约 40MB；旧实验目录有 348 个插件。没有扩大插件集合或把实验复制文件视作正式产物。
- 干净产物上播放器 116 项 JVM 测试全部通过、无跳过，包括真实 VLC 的暂停加载、定位 12000ms、继续播放、暂停位置稳定（MP3），日志 `/tmp/lmusic-vlc-clean-runtime.log`。再以 45 秒 PCM WAV 重跑相同原生测试也通过（`/tmp/lmusic-vlc-clean-wav.log`）；WAV 有 FFmpeg parser-not-found 警告，但实际准备、定位及状态断言通过。两次运行均无 UI/有声输出，不代表完整 VLCPlayback/Room/App 系统控制集成已经验收，也不覆盖所有格式或其他桌面 OS。
- Android Release 构建通过（本轮只改 jvmMain，APK 任务 UP-TO-DATE），同一 Release 再次覆盖安装小米 74e1826f 与 emulator-5556 均 Success，没有操作小米 UI。Desktop 原生初始化这一已知阻塞已解决；Latest 命令接管、真实适配层历史恢复/并发、打包 App 运行与跨平台剩余验收继续推进，四阶段未完成。

### 本轮落地：同步边界（2A）

- 新增可独立测试的 `PlatformQueueBridge`：应用队列修改和平台快照回写共用串行边界。应用更新返回前等待平台应用完成，替代后台 Flow 延迟镜像。
- Timeline 数据查询在锁外执行；用代次拒绝旧查询和差量应用过程中的中间回调，应用完成后重新读取平台最终快照。元数据刷新不反向修改原生列表。
- 差量计算保留在后台线程；回到 Main 后先核对原生列表是否仍与计算基准一致，变化则重新计算。补丁按原始索引从后往前应用。
- 数据库返回结果按 Timeline ID 顺序重新组装，保留重复项；暂时查不到但队列中已有的歌曲继续保留。完全未知的条目使本次快照暂缓回写，不压缩列表导致 current 错位。
- 随机下一首/上一首等待自定义命令返回；不修改 QueueControlPlayer 的随机插歌规则。错误回调查库后再次检查当前歌曲，避免切歌后对旧歌曲发起重试。
- 新增 6 项测试，覆盖旧查询覆盖、新旧平台事件竞争、中间回调、应用完成等待、元数据不反写、25 组差量列表组合及 Timeline 缺项/乱序/重复项。

这是同步加固，不是 Media3 命令事务：远程 Session 最终播放成功仍依赖回调；系统操作与应用操作的完整意图区分、同一首歌多次请求的取消代次、插入并播放的完整原子性仍未完成。同步失败会向调用者抛错，但没有自动回滚已修改的公共列表。以上作为 2B 的工作，不应据此宣称阶段 2 或 #14/#17 全部完成。

验证（2026-09-11）：播放器包下 66 项 JVM 测试通过，iOS Simulator ARM64 编译通过，最终 Android Release 构建通过。Release 覆盖安装至 Xiaomi `74e1826f` 和 `emulator-5554` 均返回 Success；小米仅安装，未操作其界面。

模拟器冷启动恢复 12 首队列；随机切歌 `01 → 04 → 03` 后顶部为 `03、04、01`，返回后当前为 `04`；外部 ACTION_VIEW 打开 MediaStore `122`，日志确认 SourceLocator 匹配，当前为 `01` 且队列仍 12 首。本轮 PID `20437` crash buffer 为空，MediaSession error=null，结束时暂停。证据：`/tmp/lmusic-queue-validation.HfycRa/phase2-random.png`、`phase2-media-session.txt`、`phase2-logcat.txt`。仍使用无封面的静音样本，不代表真实音质、封面或全部设备并发场景验收。

### 后续（2B）

新增 iOS 活动命令边界（2026-09-11）：LatestPlaybackCommand 由 Main 单线程持有，不可重入；新命令取消并等待旧命令结束，再访问引擎。等待前任清理使用 NonCancellable，保留 A→B→C 的清理依赖，避免 B 被 C 取消后 C 越过仍在清理的 A。AVPlayerPlayback 的播放、暂停、停止、选曲、定位与恢复接入，内部调用改用私有实现避免自我取消；解析/加载后再次检查取消，未加载当前歌曲时拒绝 seek。新增 4 项公共测试覆盖等待解析时暂停、三命令清理链、失败后重试、调用方取消。

此边界没有覆盖 Android/Desktop/Web，也不把公共队列修改回滚；它只取消当前在运行的命令，不能阻止历史恢复观察器在未来 DB 变化后重新发起请求。历史恢复“用户已接管”的持久语义、切歌与队列变更的整体所有权、MusicKit 原生异步完成与跨引擎切换仍需继续处理。原生组合测试使用真实 AVPlayer、VolumeFadeHelper 和命令边界，但延迟解析用 Deferred 控制，不等同于完整 AVPlayerPlayback + Repository/App 集成。

后续 iOS 历史接管：HistoryQueueRestorer.claimPlaybackControl 在恢复器本次生命周期内记录接管状态，取消 delivery 并增加 generation，清除 fallback/失败重试；已算出但尚未开始的 deliver 也会检查接管标记。保留队列补全，明确放弃旧 current/position（不只是关闭自动播放）。AbstractPlayback 保存恢复器引用，iOS 的显式播放、暂停、停止、选曲、seek 入口调用接管；restore 内部私有实现不接管自己。新增 3 项恢复器测试覆盖加载中接管后迟到 DB 补全、失败后接管不再重试、尚未解析 current 时也不启动 fallback。该接管不跨 App 重启持久化，不代表已经完成身份/位置原子持久化；Android/Desktop/Web 尚未接入这一显式接管入口。

历史接管最终验证：播放器 JVM 108 项中 107 项通过，1 项 VLC 原生环境未配置而跳过；iOS 测试链接、Wasm 编译、Release 构建通过（`/tmp/lmusic-history-takeover-final.log`）。11 项 iOS 原生测试通过，新组合场景使用真实 HistoryQueueRestorer + LatestPlaybackCommand + AVPlayerEngine，仓库为可控 StateFlow：接管并暂停后补入 b，队列从 a 补全为 a,b，旧恢复回调没有再次触发，原生位置保持暂停稳定（`/tmp/lmusic-history-takeover-native.log`）。这仍不是完整 Room/AVPlayerPlayback/App 集成。Release 覆盖安装到小米 74e1826f 和独立 emulator-5556 均 Success；小米仅安装，本轮未新增 Android 运行验收结论。

活动命令边界最终验证：播放器 JVM 105 项中 104 项通过、1 项 VLC 原生环境未配置而跳过；iOS 测试链接、Wasm 编译、Android Release 构建成功（`/tmp/lmusic-latest-command-final.log`）。普通 simctl spawn 执行 10 项 iOS 原生测试全部通过，新增组合测试证明暂停取消待解析命令后，迟到结果不会再次驱动真实 AVPlayer，暂停后原生位置稳定（`/tmp/lmusic-latest-command-native.log`）。小米 74e1826f 与 emulator-5556 Release 覆盖安装均 Success；小米没有界面操作。

补充检查（2026-09-11）：AVPlayerPlayback、VLCPlayback、AudioPlayback 的命令捕获会吞掉 CancellationException，现已接入公共 reportPlaybackCommandFailure，取消（含超时取消）直接继续传播且不上报媒体错误；普通异常暂保留既有报告行为。新增 3 项 JVM 测试通过。这不是普通失败传播或原生命令完成性的完成证明。

普通失败传播前必须同时处理异步动作入口，否则直接把平台方法所有异常改为 throw 会将错误升级为未处理协程异常。defaultPlayerActionHandler 已移除 GlobalScope，使用进程级 SupervisorJob 作用域；PlayerAction/QueueAction 复用 launchPlayerAction，在无返回值的动作边界记录普通失败，取消继续传播。iOS 远程媒体按钮、Desktop 菜单与媒体通知、Web MediaSession 按钮也接入同一边界。该 helper 不串行化动作、不改变随机规则，也不让直接调用的 suspend API 吞错。系统同步回调返回的 Success 仍仅表示已接收，不能作为最终播放成功的确认。

后续普通失败传播：AVPlayerPlayback、VLCPlayback、AudioPlayback 使用的 reportPlaybackCommandFailure 现在记录后重新抛出原异常；记录器自身失败作为 suppressed 保留，取消仍直接传播。播放页和首页迷你播放器改走既有 PlayerAction；common 引擎完成事件、VLC/Web 播放结束、iOS 音频中断回调接入异步动作捕获，防止自动切歌失败成为未处理异常。平台加载就绪、旧命令取消代次、MusicKitEngine 内部吞错、音量渐变暂停的异步返回、失败后的队列/引擎一致性仍未完成。嵌套平台命令的错误通知可能重复，目前尚未收敛到单一报告所有者。

普通失败传播验证：播放器包最终测试 96 项，95 项通过、1 项 VLC 原生测试因未配置原生环境而 skipped（不是 Desktop 通过）；覆盖原异常传播、记录失败/取消、成功后续不执行，以及真实 AbstractPlayback 边界选曲失败后释放锁并可重试。日志 `/tmp/lmusic-command-failure-tests-final.log`。lplayer iOS Simulator/Wasm 编译、Android Release 构建通过（`/tmp/lmusic-command-failure-propagation.log`），Release 覆盖安装到重新连接的小米 74e1826f 与独立 emulator-5556 均 Success。小米仅安装，没有操作界面。模拟器外部打开后 PLAYING，点击播放页中部控制区后 PAUSED，再次点击后恢复 PLAYING，error=null；结束暂停，PID 11354 crash buffer 为空。证据 `/tmp/lmusic-failure-ui.png`、`/tmp/lmusic-failure-ui-paused.txt`、`/tmp/lmusic-failure-ui-resumed.txt`。这些运行证据是 Android 正常操作回归，不代表其他平台原生失败场景已验收。

动作边界验证：新增 3 项测试与原有取消保护 3 项测试均通过（共 6 项），覆盖失败后继续执行、取消不误报、取消 owner 时清理进行中动作。lplayer JVM/iOS Simulator/Wasm 编译与 Android Release 构建成功（`/tmp/lmusic-system-action-boundary.log`）。Release 已安装至独立 emulator-5556；外部打开 MediaStore 19 后 PLAYING、队列 1、error=null，结束暂停；PID 11183 的 crash buffer 为空。设备证据为 `/tmp/lmusic-system-action-playing.txt`、`/tmp/lmusic-system-action-paused.txt`。小米未连接；本轮没有进行 iOS/Desktop/Web 系统按钮的运行时验证，也没有完成平台普通失败传播。

取消加固验证：3 项 JVM 测试、lplayer iOS Simulator/Wasm 编译、Android Release 构建通过（`/tmp/lmusic-playback-cancellation-final.log`）。Release 安装至 emulator-5556，MediaStore 外部打开后 PLAYING、error=null，结束暂停（`/tmp/lmusic-cancellation-release-session.txt`）。本轮 Android 仅回归安装与播放，不证明 iOS/Desktop/Web 的真实取消竞态已验收；普通失败仍未改为向事务调用者抛出。

进行中：新增公共 `Playback.playAudio` 和原子 `selectOrInsert`，外部打开及按歌曲身份点选复用该入口。Android 的插入、编辑、整表替换和索引选曲在 `PlatformQueueBridge.editAndRun` 中等待列表应用并执行选曲，期间不允许 Timeline 查询回写改换目标。新增测试验证空队列、复用已有项、跨源同 ID、新曲插入，以及插入和 seek 之间的平台回调隔离。当前播放器 JVM 测试为 68 项通过。

仍需完成：非 Android 平台的加载取消/选曲竞争、平台命令错误与失败后的状态一致性、历史恢复与用户/系统操作的共同取消边界；不能仅凭上面的应用入口串行化认定全部命令已原子化。

2B 本轮验证（2026-09-11）：68 项播放器 JVM 测试通过，iOS Simulator ARM64 编译及 Android Release 构建通过。最终 Release 安装至小米和模拟器均成功。模拟器新增静音样本 `Queue Test 13`（MediaStore 140），原队列 12 首；外部打开后当前为 13、队列为 13 首，再次打开仍为 13 首。随机下一首为 06，显示顺序 06、13。返回操作时前台被其他应用占用，UI dump 失败，不能将旧 XML 当作返回验证证据。进程 22969 crash buffer 为空，MediaSession 最后为 PAUSED。证据为 `/tmp/lmusic-queue-validation.HfycRa/command-logcat.txt` 与 `command-media-session.txt`。后续使用独立模拟器避免交叉操作。这些证据只覆盖 Android 新入口，不证明上述剩余命令竞争或四阶段全部完成。

- 保留服务端 QueueControlPlayer 的随机队列交互与后台/耳机/通知控制能力。
- 明确应用编辑、平台播放进展、系统控制器操作三类事件；应用镜像引起的 Timeline 回调不能覆盖更新的业务命令。
- 队列版本校验覆盖等待数据库、差量同步、连续多个 Timeline 回调与用户操作并发。
- 将外部导入“插入并播放”、批量删除、历史恢复接到确定性完成的播放命令边界。
- 校验 loaded item、current key、position、play intent 一致；原生加载失败和取消不能视为操作成功。
- 先增加适配器测试，再变更状态所有权，避免系统控制与应用 UI 使用不同的随机逻辑。

## 阶段 3：#14 历史恢复

历史补全保留当前条目修正（2026-09-11）：HistoryQueueRestorer 原来用 ID.indexOf 找回先前当前项。新增两项回归分别模拟同 ID 不同源、同源同歌重复两次，中间缺失歌曲后来补齐；修复前都从预期 index=2 错选到 index=0。现在按 MediaKey 和当前 occurrence 找回索引，应用确认也比较完整 MediaKey 列表，避免把来源不同但 ID 相同的快照当成已应用。数据源碰撞测试使用可控 AudioRepository，不代表现有 Room 单 song_id 主键能存储跨源重复 ID，也不包含数据库主键迁移。iOS 原生验证脚本追加整个 HistoryQueueRestorerTest，避免这部分只在 JVM 上验收。

验证：修复前两项测试失败（`/tmp/lmusic-history-identity-before.log`）；修复后播放器 JVM 110 项中 109 项通过、1 项 VLC 原生环境缺失而跳过，iOS 测试链接、Wasm 编译及 Release 构建成功（`/tmp/lmusic-history-identity-final.log`）。普通模拟器运行脚本 23 项全部通过，其中 12 项公共恢复器测试在 Kotlin/Native 上运行，其余 11 项为既有 AVFoundation 组合及时间测试（`/tmp/lmusic-history-identity-native.log`）。不将 23 项全部描述为原生解码测试，也不声称所有队列并发/身份问题已解决。Release 安装小米 74e1826f 与 emulator-5556 均 Success，小米仅安装。

重复条目主动选曲的后续修正：旧 deliveryTarget 仅比较 MediaKey，用户从第一个 a 切到第二个 a 时旧位置恢复不会取消，新增测试已先复现失败（`/tmp/lmusic-history-occurrence-before.log`）。现在 delivery 记录 occurrence，队列观察器也根据原始历史槽位的已解析 index 判断用户改选；即便上次 delivery 已失败且 target 清空，也能识别重复条目改选。用户改选会设置 playbackClaimed、清除 fallback、取消旧 delivery 并增加 generation，防止已算出但尚未发送的通知重启。若所有歌曲已解析，则立即完成恢复器状态，不等待额外 DB 事件。新增 3 项回归覆盖加载中改选、失败后改选、全部歌曲已解析后的即时结束。Sync 事件目前仍不作为用户接管判据；系统 Timeline 与用户操作的区分仍是未完成项，不能把本修正扩大为所有平台/事件的完整接管。

上述接管也清除旧 failure，新增断言确保用户改选后不会继续保留失败提示。最终播放器 JVM 113 项中 112 项通过、1 项 VLC 原生环境缺失而跳过；iOS 测试链接、Wasm 编译、Release 构建成功（`/tmp/lmusic-history-occurrence-final-checked.log`）。普通 iOS 模拟器运行 26 项测试全部通过，其中 15 项为公共 HistoryQueueRestorer 测试、11 项为既有 AVFoundation 组合及时间测试（`/tmp/lmusic-history-occurrence-native.log`）。这不是 26 项原生媒体解码测试，也未完成系统 Sync 事件接管或全 App 验收。

最终 Release 再次覆盖安装到小米 74e1826f 和独立 emulator-5556，均 Success；本轮未操作小米界面，未新增 Android 运行时验收结论。

### iOS AVPlayer 准备与时间单位修正（2026-09-11）

- AVPlayerEngine.currentPosition 原来固定将 CMTime.value 除以一百万，忽略 timescale；已改用既有 toMilliseconds，避免实际时间单位不同导致恢复 seek 的确认不断超时。新增 3 项 iOS Simulator 原生测试已实际通过，覆盖 600/1000/44100/48000/1e9 的 timescale、小数秒和无效 timescale（`/tmp/lmusic-ios-preparation.log`）。
- AVPlayerEngine.load 现在等待状态结束 Loading 并核对原生 AVPlayerItem 已 Ready；错误向调用方抛出。KVO、完成和位置回调检查当前播放项，取消等待时只释放仍属于本次加载的项，不能释放后来选中的项。尚未证明同曲多次请求、跨引擎切换、真实恢复位置的完整竞态正确性，MusicKit 准备/完成边界也未覆盖。
- 真实 AVPlayer 缺失文件测试的第一版被 runTest/runBlocking 阻塞测试主线程，Main 无法执行；采样证据 `/tmp/lmusic-ios-preparation-sample.txt`，已停止具体测试进程，不能将这次执行视为通过。测试驱动改为有期限地处理 NSRunLoop，参考 [Apple runUntilDate 文档](https://developer.apple.com/documentation/foundation/runloop/run%28until%3A%29?language=objc)。后续执行结果另记。
- 最终原生测试运行通过：AVPlayerPreparationTest 的不存在本地文件场景 1 项（实际 AVFoundation 加载，约 5.1 秒）及 CMTime 换算 3 项，共 4 项、无跳过。验证加载错误抛出 IllegalStateException、isLoading=false、isPlaying=false；证据 `/tmp/lmusic-ios-native-preparation-runloop.log` 与 `lplayer/build/test-results/iosSimulatorArm64Test`。未使用模拟引擎替代这一失败场景；正常文件定位恢复、取消/跨引擎竞态及 iOS App 完整 UI 运行仍未验收。
- 后续增加正常文件测试后发现此前失败场景证据仍不充分：Gradle 的 `simctl spawn --standalone` 使自生成有效 WAV 也报 AVFoundation -11800 / NSOSStatus -12746，完整 boot 模拟器后仍相同，不能把当时失败自动视为缺失文件特有错误。保留 native NSError.description 帮助定位底层错误，不再只保留泛化的 localizedDescription。
- 对照同一测试二进制，改为 booted 模拟器的普通 `simctl spawn` 后，正常文件与缺失文件测试均通过。新增 `scripts/test-ios-player-native.sh`：显式要求 `LMUSIC_IOS_SIMULATOR_ID`，先链接测试，再普通 spawn；不传 no-exit-code、不跳过断言。使用 `588DC4F8-1B07-4D89-BD2F-862ABBC44113`（iOS 26.3）复跑脚本成功，共 5 项原生测试通过。正常测试实时生成 20 秒 PCM WAV，真实 AVPlayer 完成准备、读取时长、seek 至 12000ms 并按原生 currentPosition 确认、暂停后位置稳定，最后释放并删除测试文件。此测试没有启动播放或验证有声输出，也不是完整的 AVPlayerPlayback 历史恢复集成测试。
- 证据：standalone `/tmp/lmusic-ios-local-seek-diagnostic.log`、booted + standalone `/tmp/lmusic-ios-local-seek-booted.log`、普通 spawn `/tmp/lmusic-ios-local-seek-spawn.log`、重复脚本 `/tmp/lmusic-ios-native-script.log`。Gradle 默认 native runner 的对应测试仍失败，标准验证应使用上述脚本，不把它称为 Gradle 原生测试全部通过。完整媒体播放、取消/连续选曲和跨引擎仍待验收。Android assembleRelease 已完成（在 `--continue` 构建里为 UP-TO-DATE），最新 APK 再次覆盖安装到小米与独立 Android 模拟器均 Success。
- AVPlayerEngine 原生取消/播放继续验收：新增 2 项测试验证 Loading 中取消后恢复 EMPTY、迟到回调不再更新状态且同一文件可重试；同曲两个不同 AVPlayerItem 的加载发生替换时，旧调用以 CancellationException 结束，不释放新播放项，新项仍可 seek。正常 WAV 测试新增真正 play 后 currentPosition 至少推进 200ms，再 pause 确认位置稳定，不只检查 isPlaying 标记。脚本现执行 7 项全部通过（`/tmp/lmusic-ios-native-playing.log`），其中 4 项 AVFoundation 测试另外完整重复 3 次均通过（`/tmp/lmusic-ios-native-repeat-1.log` 至 `-3.log`）。样本为静音 WAV，验证时间推进而非有声音质；只覆盖单个 AVPlayerEngine 的原生边界，不能替代 AVPlayerPlayback/队列/历史/跨引擎的整链路验收。
- 本轮仅新增/增强原生测试，没有新增 Android 生产代码变化；Release 构建再次成功（`/tmp/lmusic-ios-cancellation-release.log`），小米 74e1826f 和 emulator-5556 覆盖安装均 Success。没有在小米上进行界面操作或宣称完成整链路体验验收。
- 加载期间暂停/停止的原生回归发现并修复两个实际问题：pause 未清除 autoPlayWhenReady，Ready 回调会再次播放；stop 重建 EMPTY 风格状态提前结束 isLoading，使尚未 Ready 的加载调用报错。修复为 pause/stop 都撤销待播放标记，stop 保留媒体准备状态，仅停止播放并归零位置。新增两项测试修复前分别失败（错误为“Expected value to be false”和“AVPlayer did not prepare the selected item”），修复后连同原有回归共 9 项原生测试全部通过，无跳过。证据 `/tmp/lmusic-ios-pending-play-before.log`、`/tmp/lmusic-ios-pending-play-after.log`。这是 AVPlayerEngine 直接命令的验证；AVPlayerPlayback 尚在 resolve/load 等待时用户暂停的请求所有权与音量渐变异步返回，仍需上层整合处理，不能用该引擎修复宣称已全部解决。
- 暂停/停止修复的 Release 构建成功（`/tmp/lmusic-ios-pending-play-release.log`），因仅改 iOS 生产代码，APK 任务 UP-TO-DATE。小米 74e1826f 和独立 emulator-5556 再次覆盖安装均 Success。本轮没有新增 Android UI 验收结论。
- 音量渐变暂停的完成边界：VolumeFadeHelper 新增结构化 pauseAndAwait，等待淡出及 native pause 回调，调用方取消会取消其中工作，native 失败继续向上抛出；iOS AVPlayerPlayback.pause 改为等待该方法。Android AudioSink 保留同步 pause 入口，命令先注册再返回，后台等待失败交给动作边界记录；不能将此同步入口解释为等待 native 完成。play 即使禁用淡出也会取消旧暂停。新增 5 项公共测试通过，覆盖等待回调、失败原因为调用方可见、父级取消、后来的播放取消暂停、淡出到零后才调用暂停（`/tmp/lmusic-pause-completion-final.log`）。原异常对象断言因 coroutine stacktrace recovery 复制异常而修正为内容和原始 cause 检查，不是吞错兼容。
- 新接口只解决渐变暂停过早返回，不解决 resolve/load 期间用户意图代次、跨引擎迟到回调或正在等待的选曲在暂停后又执行 play；这些上层命令所有权仍未完成。真实 AVPlayer 渐变暂停及 Android 回归结果另记。
- 最终验证：9 项 iOS 原生测试通过，其中正常 WAV 用真实 VolumeFadeHelper 淡出并等待 AVPlayerEngine.pause，返回后原生位置稳定（`/tmp/lmusic-ios-native-fade-pause.log`）。5 项 JVM 公共测试、iOS/Wasm 编译和 Android Release 构建成功；Release 安装小米 74e1826f 与独立 emulator-5556 均 Success。Android 外部打开 MediaStore 19 后 PLAYING、error=null，系统暂停后 PAUSED 于 21301ms，PID 12016 crash buffer 为空。证据 `/tmp/lmusic-fade-playback-playing.txt`、`/tmp/lmusic-fade-playback-paused.txt`。小米仅安装；没有把这些引擎/渐变级测试当作完整历史或用户命令竞争验收。
- Android Release 构建通过并覆盖安装到小米 74e1826f 和 emulator-5556，均 Success；本轮仅变动 iOS 生产代码，Android APK 无需重新编译的任务为 UP-TO-DATE。模拟器外部打开 MediaStore 19 后 PLAYING、error=null，结束暂停。证据 `/tmp/lmusic-ios-preparation-android-session.txt`。小米仅安装。

进行中（2026-09-11）：增加 `historyQueueIdentityV2`，在同一身份记录中保存有序 ID、各项来源及精确 index；旧 ID 历史与无效 V2 记录回退兼容。部分恢复继续保留缺失项及其来源；重复歌曲用原始槽位定位。历史 current 已知来源时，settlement 只观察该来源，来源移除/停用视为终态。Android 系统恢复入口复用同一快照解析，并保留可解析 current 的位置，不再固定为第一首 0ms。

已确认并修复 Android 后台读取 MediaBrowser 位置被线程检查拒绝、随后静默记录 0ms 的问题：主线程采样提供后台安全快照，UI 主线程仍直接读取以保持流畅。初轮独立模拟器验证中，暂停 15425ms 后重启，MediaSession 仍为 15425ms；同时发现暂停 UI 没有重新采样，已补充低频刷新，等待最终安装回归。历史记录也保持暂停时低频采样，并补充暂停 seek 持久化测试。

未完成边界：legacy 缺失 current 的等待仍可能受无关源影响；非 Android 实际历史加载 hook、恢复失败与用户/系统操作取消尚需收敛；持久化身份与 position 仍分开存储。上述工作及完整运行时验证全部保留，不将当前实现视为阶段 3 完成。

本轮最终验证：79 项播放器 JVM 测试通过，iOS Simulator ARM64 编译、Android Release 构建通过。Release 安装到独立 emulator-5556 成功；小米在本轮断开，最新版本尚未补装。模拟器恢复暂停位置 15425ms 后，UI 正确显示 00:15；继续播放再暂停、强制结束进程后冷启动，位置由暂停瞬间的 21317ms 恢复为 21323ms（6ms 差异），UI 为 00:21，当前仍为 Queue Test 01，未自动播放。最终 PID 6763 crash buffer 为空。证据：`/tmp/lmusic-queue-validation.HfycRa/history-final-before.txt`、`history-final-after.txt`、`history-final-restored.png`、`history-final-logcat.txt`。此项验证不覆盖缺失来源、系统通知恢复、iOS/Desktop 实际加载或删除事务。

后续 Android 验证环境：独立 AVD `codex_lmusic_validation`，端口 5556，Android 36.1 ARM64 16KB 镜像；以 `-no-window -no-audio` 后台运行，不再操作共用的 Pixel_9a / emulator-5554。AVD 为本任务新建，避免清理或重置原设备数据。

- 新历史记录保存来源和歌曲 ID，以及重复歌曲当前出现位置；兼容旧 ID 历史。
- 明确目标来源已结束首轮处理时即可回退，不能被无关 Loading 来源阻塞。
- Android `onPlaybackResumption` 与应用入口使用同一恢复语义，保留 current/position。
- 核查非 Android `onQueueRestored`：当前默认 hook 不会真正加载歌曲和应用位置，不能仅恢复 common 列表就标记完成。
- 覆盖用户在部分恢复队列中切歌、编辑、停用源，及迟到恢复数据不覆盖新操作。

身份边界：当前 Room 表仍以 `song_id` 为单主键。MediaStore 使用完整 URI 构造 ID，Subsonic 使用服务返回 ID。MediaKey 能阻止队列串源，但不能让数据库存下两个相同主键；若要支持该情况，必须单独迁移数据库主键及关联表，不能声称只改播放器就已解决。

### 最近一轮恢复加固记录（2026-09-11，阶段 2/3 继续推进）

- 旧历史无法定位来源时，15 秒后允许回退；这不会取消扫描、修改歌曲可用性或停止后续补全。已知目标来源不使用这个兼容期限。
- 回退现在真正调用平台加载，成功前不标记 currentRestored，也不把旧 position 绑定到回退身份。
- 恢复加载与数据库观察分开；用户切换当前项时取消旧加载，但保留未解析项的后续补全。加载失败保留 Pending.failure，数据库刷新后允许重试。
- 非 Android 只在具体播放器初始化后启动历史恢复和录制。iOS 等待 engine 就绪及定位；Web 等待媒体元数据，修正毫秒/秒定位单位；Desktop 接入原生暂停加载、定位确认。以上仍需平台运行验收，不能以接入 hook 视为完成。
- Web 编译为完成验证修复两处遗留问题：FontFileStore.delete 的 Result<Unit> 返回类型，以及 BrowserMediaSessionHelper 的旧 import/队列包装引用。
- 最终 Android Release、iOS Simulator ARM64、Wasm lplayer 模块编译通过。最新 Release 安装到独立 emulator-5556，暂停恢复为 21323ms，UI 为 00:21。小米仍未连接，待补装。
- 共执行 86 个播放器测试：85 项逻辑测试通过，新增 VLC 原生测试失败。失败发生在 MediaPlayerFactory 创建实例时，早于加载、seek 和本轮恢复逻辑；带/不带测试初始化参数均失败，不能认定 Desktop 验收通过。
- `:composeApp:vlcSetup` 成功，下载并生成了 3.0.21 universal 运行库到 `lplayer/src/jvmMain/assets/macos/vlc`（生成资源未提交）。后续排查过滤后的插件/运行库，保留原生测试，不把失败改成通过或隐藏。
- 证据：`/tmp/lmusic-restore-verified.log`、`/tmp/lmusic-vlc-native-all.log`、`/tmp/lmusic-vlc-default-test.log`。原生测试需配置 LMUSIC_NATIVE_RESOURCES 和 LMUSIC_NATIVE_AUDIO_FIXTURE；未配置时明确标为 skipped。
- `ht-api-detect` 尝试查询后服务启动失败（debug workspace 无编译产物）；使用缓存 vlcj 4.11.0 sources 声明及实际编译补充签名证据，未宣称工具验证成功。VLC 暂停启动选项另参考 [VideoLAN 官方变更记录](https://mailman.videolan.org/pipermail/vlc-commits/2015-November/032876.html)，运行正确性由原生测试独立验证。

### 阶段 3 下一步：历史记录整体提交与位置归属（2026-09-11 核查）

位置归属迁移第一步（进行中）：新增内部 HistoryPositionProvider 能力，公共历史采样器优先请求可校验位置，并在挂起返回后复核队列快照的引用身份与恢复状态。Desktop 在自己的命令 dispatcher 上检查 LatestPlaybackCommand 是否正在执行、快照是否仍为当前队列、loadedMediaKey 是否匹配、native 是否 PAUSED/PLAYING；STOPPED 且已清除加载身份时可返回 0。加载、替换和失败状态返回 null，不回退到 UI 的旧进度值。其他平台本轮仍走原来的 currentPosition 路径（增加队列快照复核），尚未完成原生身份验证。这不是新旧平台能力已统一的声明，也没有完成重复项请求代次或整体记录单次提交；后续仍按下列完整要求推进。

第一轮验证：128 项 JVM 测试全部通过、无跳过，其中真实 VLCPlayback 测试增加到 5 项，覆盖加载期间无可持久化位置、暂停可采样和队列替换拒绝旧媒体位置。iOS Simulator / Wasm 编译已通过。联合构建 `/tmp/lmusic-history-position-guard.log` 的 Release 仍在 R8 阶段运行，尚未安装本轮新包；不能将之前安装的 APK 当作本轮结果。其后补充的两项公共回归（挂起采样期间队列替换、原生 null 不回退 UI 值）与同 ID 不同来源断言需在该构建结束后复跑。队列快照引用检查不是原生请求 generation，StateFlow 可能合并相等值，因此仍需补齐重复项与等值重选的完整所有权。

当前代码证据：

- PlaybackHistoryImpl.startRecording 有独立的队列持久化订阅、回退位置清零订阅和每秒位置采样订阅。HistoryStorageImpl 把 V2 身份 JSON 与 historyPlayPosition 分别写入 KV；restoreFromHistory 也分两次读取。因此 V2 保住了队列内 ID/来源/index 的关系，但没有保证该记录与 position 是同一批数据。
- KVSaver 只提供单键 readData/saveData；KVItemImpl.setData 逐项调用 saveData，没有多键事务协议。不能用“几个 setter 紧邻执行”证明进程中断时一致，也不能在没有后端证据时承诺一次字符串写入等于 fsync 持久化。
- Playback.currentPosition 只返回 Long，不包含 native 已加载媒体的来源、ID 或请求代次。队列先改变而平台还在解析/加载时，直接把这个 Long 和新队列打包仍可能串位。整体 JSON 写入只能解决记录撕裂，不能单独解决位置归属。
- LPlayerSettings 的“清除播放进度记录”直接写 historyPlayPosition=0；位置采样器随后仍会再次写入。现有 LPlayerSettingsTest 手工构建镜像 SettingsGroup，并不调用生产 provideLPlayerSettings，且只验证点击当下归零，没有覆盖持续记录、下次启动或新的整体记录。因此迁移不能遗忘这个独立写入口，也不能以现有设置测试作为生产入口或端到端清除语义证明。

实施顺序（未实现，不作为完成证据）：

1. 先明确平台位置快照契约：仅对已加载且仍属于当前选择的媒体返回可持久化位置，同时携带来源身份与选择代次/重复项信息；加载中、切换失败和旧回调不能伪装成新歌曲位置。UI 的普通 currentPosition 可以保留，不用让 UI 依赖原生播放器。
2. 收敛历史写入者：队列变更、有效位置采样、恢复回退、用户清除都提交到同一记录器；不得由多个观察协程分别拼接和覆盖记录。保持部分恢复时未解析槽位、来源和精确重复项 index，不能为迁移而丢弃旧历史。
3. 使用一个带版本的完整记录保存队列身份、current index 和已验证位置；恢复先读取完整记录一次。旧 V2/legacy 只用于兼容读取，迁移成功后不再把它们与新记录字段混读；格式损坏、未知版本和清空历史需有明确行为，不能因旧键残留复活已清除的数据。
4. “清除播放进度”通过同一记录器处理，兑现“下次启动从头播放”的既有文案；明确防止本次会话的后台采样立即覆盖清除请求，不把直接修改旧 KV 键保留为旁路。
5. 验证保存失败/重启读取、队列切换与采样交错、同源重复项、不同来源同 ID、部分恢复和回退、清除与采样竞争；以真实平台快照和实际存储冷启动补充运行时证据。单个内存存储测试、单键 JSON 编解码和平台编译都不足以宣布阶段 3 完成。

此核查保留既有四阶段范围，没有改写存储协议或变更 APK。下一轮应先落实位置归属契约与记录器测试，不直接把旧三个写入口包进同一个 JSON 后宣称原子恢复已完成。

## 阶段 4：#17 Sandbox 一致性

2026-09-11 重新读取远程 #17 的范围校准：Issue 的“Snapshot 与数据库一致性”明确要求数据库提交失败时保留已复制文件和最近成功 Snapshot，并向用户显示可重试错误。因此此前本文提到的“导入端到端回滚 / 数据库提交失败完整回滚”不能解释为自动删除已经安全导入的 Sandbox 文件；应验证保留文件、明确失败、不提前播放，以及重试入库后不重复导入。复制/格式校验/索引扫描失败的未完成导入清理，与用户主动删除时的可回滚隔离事务，是不同阶段，仍保留原有要求。原文件永远不因导入失败被删除。

远程 #14 关闭条件仍包括多数据源组合矩阵、封面/模糊背景/歌词重试端到端和四端最小运行时冒烟；#17 明确不包含 Desktop 系统文件关联或 Web 外部打开，也不重构全局歌曲 ID。后续优先补齐这些实际要求，不能将底层 VLC 测试、不断扩展的异常矩阵或不在本期的系统关联开发替代原需求验收。

### 索引与删除恢复记录的原子替换（2026-09-11）

损坏索引修复的最终验证：27 项 JVM Sandbox 测试、iOS Simulator 模块编译、Android Release 构建通过（5m47s）；最新 APK 覆盖安装小米 74e1826f 与独立 emulator-5556 均 Success，小米只安装。模拟器用新生成的 45 秒、内嵌绿色 JPEG 封面的 MP3 导入并播放，队列 1 项、error=null；暂停后 force-stop，普通冷启动恢复 PAUSED 17441ms，继续播放到 23472ms，两个进程 crash buffer 为空，结束暂停。证据 `/tmp/lmusic-corrupt-index-restarted.txt`、`/tmp/lmusic-corrupt-index-playing.txt`。此运行验证正常索引兼容，不是设备损坏索引故障注入。

封面对照证据（#14）：截图 `/tmp/lmusic-cover-validation.png` 与冷启动后 `/tmp/lmusic-cover-restarted.png` 均实际显示绿色内嵌封面，标题 LMusic-Cover-Validation；`/tmp/lmusic-cover-validation-log.txt` 明确记录对应 MediaCoverRequest 的 Successful (MEMORY/MEMORY_CACHE)。说明此 Android Release 的 Sandbox 内嵌封面加载链路有效，不能再把所有无封面测试素材的同名异常直接视为 Fetcher 未注册。无封面返回 null 的错误提示、来源从未就绪到就绪后的封面/歌词重试、歌手专辑与其他平台仍未验收。另观察到 MediaSession description 的标题显示 LMusic-Test（歌手），而 App 标题正确，需单独检查系统元数据映射，不能将其当作 UI 标题已错的证据。

后续损坏索引修复：ensureIndexLoaded 原本把 JSON/读取异常吞掉并缓存空索引。新增回归先证实截断 JSON 后 import 仍成功，可能覆盖旧索引（`/tmp/lmusic-corrupt-index-before.log`）。现在仅在索引不存在时使用空索引；存在但无法读取/解析时保留原文件并抛错，CancellationException 继续传播，不缓存空值，也不发布假成功 Snapshot。通过现有 SnapshotState.Error 在 Sandbox 页面显示错误。新增两项真实文件测试覆盖损坏索引拒绝导入、原件/临时目录不变，以及已重命名歌曲在重新创建 Source、读取失败、恢复有效索引并 refresh 后仍保留原 ID。Sandbox 总计 27 项测试通过，无跳过，最终构建日志 `/tmp/lmusic-corrupt-index-final.log`。这一步是防止继续破坏，不是自动修复损坏索引；用户可操作的恢复/备份流程仍待实现。独立 emulator-5556 拒绝 adb root，未做 Release 私有目录的故障注入，不将 JVM 文件故障测试描述成真机故障验收。

- 原先直接 writeString 覆盖索引与删除 journal，写到一半失败可能留下损坏 JSON。现在共用 SandboxAtomicFileWriter：在目标同目录写 `.tmp`，写完检查取消，再原子替换；索引内存值只在替换成功后发布。替换与内存发布置于同一 NonCancellable 边界，避免 FileKit 从 IO 返回时取消造成磁盘已更新而内存未更新。写入/替换失败保留旧正式文件，并清理本次临时文件；清理失败作为 suppressed error 保留。
- 仅用于 Sandbox 私有本地文件，调用由现有 operationMutex 串行化，不用于外部 content URI。FileKit 0.12.0 的 JVM/Apple 与 Android FileWrapper 路径委托 kotlinx-io SystemFileSystem.atomicMove；Android UriWrapper 是复制后删除，不能视为原子替换。ht-api-detect 已按技能执行查询、一次服务恢复及最终查询，但未找到扩展符号；以上依据当前依赖 sources 声明及实际测试/编译，不声称技能已证明 classpath 可达性。
- 新增 6 项真实文件测试：成功覆盖与内存发布顺序、部分写入失败、替换失败、写入期间取消、替换期间取消、遗留临时文件不被提升为正式索引。连同现有 Sandbox 测试共 25 项通过、无跳过。iOS Simulator 模块编译通过；Release 及设备结果随后补充，构建日志 `/tmp/lmusic-sandbox-atomic-index.log`。
- 这不是文件 fsync/目录 fsync 的断电持久化保证，也没有新增重命名/导入的完整崩溃事务日志。进程中断后遗留的删除 journal `.tmp` 不会被当作有效 journal 读取；其清理策略、损坏旧索引的处理、真实文件+Room+原生播放器故障矩阵仍待推进，阶段 4 未完成。
- 最终构建成功（5m41s），Release 安装到小米 74e1826f 和 emulator-5556 均 Success，小米仅安装。独立模拟器导入新生成的 45 秒静音 MP3 `LMusic-Atomic-Validation.mp3`，实际进入 Sandbox 并播放，队列 1 项、error=null。暂停并 force-stop 后普通冷启动（不再次发送文件），恢复到 PAUSED 12911ms，继续播放到 18937ms；重启前后来源、路径及 ID `audio_b756901e825368edcd28745ca38ab5fa` 保持一致，两个进程 crash buffer 为空，结束已暂停。证据 `/tmp/lmusic-atomic-import-session.txt`、`/tmp/lmusic-atomic-restarted-session.txt`、`/tmp/lmusic-atomic-restarted-playing.txt`。本轮未模拟进程在写入瞬间被杀，也未做 iOS 原子写入运行时验收。既有 MediaCoverRequest fetcher 错误仍出现，未宣称封面问题已解决。

### 4A：导入入库确认（2026-09-11）

- 外部打开现在接受 `Committed.revision >= targetRevision`，同时观察数据库目标行，校验 ID、来源和 available；不再因中间 revision 被 StateFlow 合并而一直等待，也不接收同 ID 的其他源歌曲。
- 同版本或更高版本提交失败立即反馈；来源停用/切换不使用残留数据库行。协程取消继续传播，入库等待超时仍向用户提示。
- 新增 6 项测试通过，覆盖更高版本、数据库延迟、串源/不可用、旧版本等待、失败、停用及超时。Release 构建通过并安装到独立 emulator-5556；MediaStore 19 外部打开后 PLAYING、error=null，结束暂停。小米未连接。
- Desktop 原生测试补充环境变量输入并禁止显式原生测试使用缓存，防止旧运行库结果被复用。完整插件、库目录和权限调整未解决打包产物的初始化失败，相关生产配置试验已撤回；重复默认 discovery 也已从原生测试中去除，失败仍存在。此前完整运行库通过仅是一次历史记录，不作为当前打包产物验收结论。
- 证据：`/tmp/lmusic-stage4-release.log`、`/tmp/lmusic-vlc-single-discovery.log`。本小步不包含下面的删除事务与真实文件系统回滚测试，阶段 4 仍未完成。

- 由应用协调层连接文件操作、数据库提交与播放器，不让 lmedia-core 依赖 lplayer。
  - 进行中：UI 删除入口已接入应用层 `SandboxFileOperations`。源将文件移入私有隔离目录、发布移除快照；应用层等待对应或更高 revision 的数据库提交和目标歌曲不可用，再按 MediaKey 移除播放队列，确认后永久删除隔离文件。失败先恢复文件与索引，再发布恢复快照并确认。
  - 持久化删除恢复记录供下次初始化恢复未完成删除；隔离目录不会进入扫描。冲突时不覆盖原路径文件，保留隔离副本并抛出错误。当前没有面向用户的恢复冲突处理页面。
  - 新增 9 项测试通过（5 项隔离事务、4 项实际源/索引/快照测试），连同已有 4 项身份测试合计 13 项通过。真实文件系统参与测试，源级测试仅替换 Taglib 元数据解析；不以此代替平台原生解码和真实数据库/播放器验证。重复摘要导入不退出 Loading 的问题一并修复。
  - 最终 Release 和 lmedia-core iOS Simulator ARM64 编译通过（`/tmp/lmusic-sandbox-source-verified.log`）。安装至独立 emulator-5556 成功，小米未连接。从 `file:///sdcard/Download/LMusic-Delete-Validation.mp3` 导入后正在播放，Sandbox 1 项、MediaSession 队列 1 项；页面确认删除后显示 0 项和“文件已删除”，MediaSession NONE、队列 0、error=null，PID 9059 的 crash buffer 为空。Download 外部原件仍在；永久删除的是此次自行生成的 Sandbox 测试副本。
  - 运行证据：`/tmp/lmusic-sandbox-before-delete.png`、`/tmp/lmusic-sandbox-after-delete.png`、`/tmp/lmusic-delete-before-session.txt`、`/tmp/lmusic-delete-after-session.txt`、`/tmp/lmusic-sandbox-delete-device.log`。本轮只验证删除队列唯一且正在播放项，尚未验证多项队列切换与真机故障注入。
  - 新运行时发现：`MediaCoverRequest` 报 `Unable to create a fetcher that supports`。不能归因于静音测试文件没有封面，需要检查 Fetcher 注册/匹配；保留为 #14 封面验证的未完成项。
  - 删除事务仍需覆盖：多项队列切换、源停用与删除并发、恢复快照再次提交失败，以及恢复记录损坏/冲突的用户处理路径。重命名的数据库确认和导入端到端回滚仍待完成，不能把本次删除正常路径验证扩展为阶段 4 全部完成。
  - 最后复查将内容 Ready 明确置于下游确认之前，并避免重复增加 generation；13 项测试、iOS Simulator 媒体源编译、Release 构建再次通过（`/tmp/lmusic-sandbox-delete-final-verified.log`）。该最终 Release 已覆盖安装至 emulator-5556。
  - 导入移动后回滚已接入：索引更新或扫描失败/取消时，在 NonCancellable 中清理本次临时/目标副本并恢复旧索引；不删除外部原件。扫描中没有目标歌曲时不再返回临时元数据伪造成功，发布 Snapshot 前必须找到目标。新增 3 项真实文件系统故障测试通过，Sandbox 合计 16 项通过；覆盖已有索引/快照保留、扫描抛错、扫描解析为空及取消。索引写入本身的 I/O 故障、进程中断、应用层数据库提交失败仍需专项验证；重命名下游确认仍待实现。
  - 本轮导入最终验证：16 项 Sandbox 测试、iOS Simulator 媒体源编译和 Android Release 均通过（`/tmp/lmusic-import-rollback.log`）。安装至 emulator-5556 成功；同一 Download 测试文件连续打开两次，MediaSession 均 PLAYING、error=null、队列 1 项，Sandbox 页面仍 1 项并显示“重新扫描”（未卡 Loading）。PID 9529 crash buffer 为空，结束已暂停。小米未连接。证据：`/tmp/lmusic-import-check.xml`、`/tmp/lmusic-import-rollback-before-session.txt`、`/tmp/lmusic-import-rollback-after-session.txt`。
  - 重命名下游确认已接入应用层：等待同源同 ID、可用且路径与目标一致的数据库行，并等待队列元数据刷新到该路径，才返回成功；不重新选曲或改变队列位置。源在发布前确认扫描包含改名目标。下游失败时恢复旧文件/索引，发布恢复快照并再次等待确认；恢复失败保留为原异常的 suppressed exception 并记录日志，不再静默吞掉。
  - 新增 4 项测试通过（源级 3 项、入库路径确认 1 项），Sandbox 19 项、应用入库确认 7 项合计 26 项通过。覆盖稳定 ID、目标冲突、数据库确认失败后的文件/索引/快照恢复、旧路径不能因 ID/revision 已匹配就被接受。进程中断与恢复失败的持久化处理、真实数据库故障注入和各平台正在播放文件的重命名仍需验证。
  - 重命名最终验证：26 项测试、iOS Simulator 媒体源编译、Android Release 构建通过（`/tmp/lmusic-rename-confirm.log`），安装至 emulator-5556。正在播放时将测试副本改名为 LMusic-Renamed-Validation.mp3，UI 退出编辑且显示新文件名，队列仍为 1 项，PLAYING 位置从 52719ms 继续到 55723ms、error=null。随后暂停、force-stop、冷启动，页面仍为新名称，继续播放成功；验证结束再次暂停。PID 9747 / 10094 的 crash buffer 均为空。小米未连接。本轮不代表 iOS/Desktop 正在播放时重命名已通过。
  - 重命名设备证据：`/tmp/lmusic-rename-after.xml`、`/tmp/lmusic-rename-immediate-before-session.txt`、`/tmp/lmusic-rename-after-session.txt`、`/tmp/lmusic-rename-restarted.xml`、`/tmp/lmusic-rename-restarted-playing.txt`。
  - 文件操作与停用的锁顺序已收敛：Repository.withEnabledSource 复用 setSourceEnabled 的每源 mutex，外部打开、导入、重命名和删除在该边界内完成确认/回滚；快照提交继续使用独立 committer mutex，因此事务等待提交时不会阻止提交，停用只能在事务退出后进行。不新增后台 launch/UI 状态包装。block 不允许嵌套修改启用状态。
  - 新增 4 项并发测试通过，连同既有 8 项提交测试和 7 项应用入库确认测试共 19 项通过；覆盖停用等待恢复提交、停用先发生时拒绝文件操作、取消后等待 NonCancellable 恢复，以及其他源独立执行。历史 settlement 回归测试也通过。最终 lmedia-data iOS Simulator 编译和 Release 构建通过（`/tmp/lmusic-source-operation-final.log`）。构建中 Kotlin JVM 增量编译曾自动回退，最终任务成功。
  - 最终 Release 安装至 emulator-5556，关闭 Sandbox 后 UI 为“媒体库 3 首 · 可用 2 · 不可用 1”；外部打开 MediaStore 19，日志确认 SourceLocator 匹配，MediaSession PLAYING、error=null。原 Sandbox 项仍留在队列中作为不可用项，外部匹配歌曲加入后队列 2 项；本轮没有将“停用源时自动移除队列”宣称为已实现。PID 10419 crash buffer 为空。结束恢复 Sandbox 启用并暂停播放，小米未连接。证据：`/tmp/lmusic-source-disabled.xml`、`/tmp/lmusic-source-disabled-play.txt`、`/tmp/lmusic-source-restored.xml`。
  - 尚需补充：真实 Repository/数据库故障注入的同时停用验证，以及四个平台上的命令完成边界；上述锁协议单元测试不等同于全部平台并发验收。
  - 已补一项真实 Room + Repository 集成测试：在 DAO 提交入口注入一次异常，验证失败提交不改变既有记录，恢复快照实际写入后等待中的停用才执行，数据库最终不可用；重新启用后新快照仍可正常写入。测试使用可注入 CoroutineScope 并在结束时取消/join，生产默认作用域行为不变。这是 DAO 边界故障注入，不是物理磁盘故障、真实文件事务或原生播放器的完整集成测试；也没有将未获消费确认的迟到快照当作测试证据。
  - 最终集成测试、lmedia-data iOS Simulator 编译和 Release 构建通过（`/tmp/lmusic-real-database-final.log`）。Release 已安装到独立 emulator-5556；外部打开 MediaStore 19 后 PLAYING、error=null，结束暂停。小米未连接。设备记录：`/tmp/lmusic-real-database-release-session.txt`。
- 文件先移到隔离区，更新索引及 Snapshot，确认数据库和播放队列已处理，再永久删除；失败恢复文件和索引并重新提交恢复 Snapshot。
- 快照等待允许目标 revision 已被更高 revision 覆盖，但必须核实目标歌曲的来源和数据库结果。
- 增加真实文件系统测试：导入/重复/同名异内容/损坏/并发、重命名与删除回滚、删除正在播放的文件。
- Android Release 真机回归，iOS 外部文件权限与实际播放回归；平台编译通过不等同于真机验证完成。
