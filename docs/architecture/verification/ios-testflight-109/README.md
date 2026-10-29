# iOS TestFlight 109 回归修复（2026-10-29）

## 当前结论与提交前 review

2026-10-29：用户已确认旋转/播放闪退不再出现，并在最终修复包中确认歌词定位正常。最终修复包括精确时间参数桥接、零容差 seek、独立资源串行队列及预期封面失败日志降噪。最终本地 iOS 播放回归 70 项通过；此前共享缓存与布局相关回归 83 项通过。真机普通 Debug 包已保留数据覆盖安装成功。本次不宣称已重新完成修复后 PCM 样本比对，也未发布新的 TestFlight 包。

提交前 review 检查缓存淘汰锁与占用保护、资源请求取消/关闭的串行访问、原生选项字典桥接、旋转时布局身份同步以及诊断入口残留，未发现阻止提交的问题。测试素材为生成的静音 FLAC，不包含用户音频、原始设备日志或凭据。以下按时间保留调查记录，其中“待验证”描述的是当时阶段。

## 用户反馈与证据

- 横竖屏切换时，任意页面可能闪退。
- WebDAV 歌词和声音不同步，偏移类型尚未确认。
- 后续出现正常播放崩溃，重新进入也会崩溃。
- 用户提供旋转/播放两份 TestFlight 报告；原始报告仅在本地分析，不提交设备标识和用户数据。

## 旋转崩溃

当前代码在 iPhone 17 / iOS 26.5 模拟器复现：`LayoutCoordinate operations are only valid when isAttached is true`，堆栈指向 Compose 1.11 `BoundsTransformDeferredAnimation.updateTargetOffsetAndAnimate`。其缓存的 motion-frame 父坐标仍指向横竖屏切换前已卸载的 bottom sheet。

首页历史面板、详情元数据、搜索关键词均使用 bounds 动画。新增 AdaptiveLookaheadScope，只在实际自适应容器身份改变时重建这些局部动画节点；保留常规布局尺寸变化的动画。BottomBarApplier 将实际容器身份随 movableContent 参数同步传递，通过 CompositionLocal 下发，避免组件各自采样窗口尺寸时的时序差异。导航状态和播放核心不重建。

回归测试模拟共享 lookahead 根下跨已卸载的 motion-frame 父节点移动：旧实现稳定抛相同异常，修复后六次切换通过。真实模拟器初版仅采样窗口尺寸仍复现，因此改为显式父容器身份；最终模拟器多次首页/历史页横竖屏切换不再出现该异常。

## 播放／重启崩溃

TestFlight 符号化报告定位 `RangeContentCache.evict`：Kotlin/Native 的 `HashMap.EntryRef` 会在所属 map 结构变化后失效。`entries.filter(...).sortedBy(...)` 只复制 EntryRef 列表；删除第一项后访问下一项触发 ConcurrentModificationException。并非缺少互斥锁，而是同一锁内保留了失效的 map entry 引用。

修复为先复制独立的 key/value Pair，再按未占用和 LRU 顺序淘汰。保留使用中的缓存、配额和缓存可用性通知语义。不清空用户曲库、播放历史或全部缓存。

新增连续淘汰多首和冷启动清理旧缓存测试：旧实现在 iOS 原生测试中两项均失败；修复后 ContentCacheTest 的 iOS 原生 16 项及 JVM 回归通过。

## 尚未确认

WebDAV 歌词与声音不同步尚无足够证据定位，未添加固定时间补偿或未经验证的音频时钟改动。优先恢复稳定运行后，继续对照具体歌曲、seek 前后及缓冲状态验证。

## 最终本地验证与真机安装

- 完整内容缓存模块：JVM 37 项、iOS Simulator 原生 36 项通过；布局/查询相关 lhome 6 项、lsearch 4 项通过，共 83 项，零失败。
- iOS Simulator 与 iPhone Debug 全应用构建成功；最终模拟器首页和历史页多次横竖屏切换正常。
- 已向用户连接的 iPhone 保留数据覆盖安装本地 Debug 验证包（不是新的 TestFlight 包），成功启动，并读取到已有歌曲与歌词；启动数分钟后进程仍在运行，所采集日志未发现未捕获异常。
- 真机旋转、连续播放及歌词与声音同步的体验结果仍待用户反馈。启动成功不等于这三项已完成真机验收。

## 真机反馈：缓存重播歌词偏移（继续排查）

用户确认旋转和正常播放不再闪退。WebDAV 完整缓存后重播任意歌曲均可能出现数秒级偏移，歌词通常偏快；seek 有时修正，有时变成偏慢，使用的是手机扬声器。

当前收紧 AVURLAsset 精确时长/时间索引选项，以及 AVPlayer seek 的前后容差为零，作为验证候选。完整缓存重播仍走相同 AVPlayer 引擎；没有添加歌词固定偏移，也未认定该改动已解释全部现象。扩展原生测试覆盖完整缓存离线重开后多次前后 seek、归零和播放后暂停；需进一步核对真实歌曲的声音对齐。

候选验证结果：使用 `scripts/test-ios-player-native.sh` 在已启动的 iOS 模拟器普通 spawn 环境运行，68 项真实原生/核心测试通过、零失败。Gradle standalone 环境有效 WAV 也无法准备的既有环境限制仍存在，不以该环境结果判断播放是否正常。缓存回归为生成的 PCM WAV，证明精确位置、离线重播和控制状态，不足以证明用户实际音乐的声音/歌词同步已修复。

实现依据：[Apple 精确时间索引选项](https://developer.apple.com/documentation/avfoundation/avurlassetpreferprecisedurationandtimingkey)、[Apple seek 容差](https://developer.apple.com/documentation/avfoundation/avplayer/seek(to:tolerancebefore:toleranceafter:))。

候选真机 Debug 包构建成功，已保留数据覆盖安装到用户 iPhone。真实缓存歌曲的歌词同步效果待用户对照重播和 seek 验证；尚未提交或发布 TestFlight。

## 《四面楚歌》样本级定位（2026-10-29，进行中）

- 用户反馈精确 seek 候选仍无效；偏差随位置变化，Android 同曲歌词点击定位正确。
- WebDAV 测试源原始 FLAC：44.1 kHz、双声道、16 bit、9,857,283 有效采样帧，时长 223.521156 秒。手机上该曲 468 个缓存片段的 SHA-256 均与源文件对应区间一致；缓存歌词文本和所有时间戳也与源文件完全一致。
- 完整解码 PCM 作为基准；macOS 与 iOS 模拟器的本地文件/独立资源加载器，在 60/120/180 秒取得的两秒 PCM 与完整解码对应区间逐字节相同。
- iPhone 静音 AVQueuePlayer 音频 tap：直接文件及独立 Swift 分段加载器取得的音频仍与目标时间一致。
- **直接使用应用 IosCachedAsset + RangeContentCache 的真机诊断复现偏差**：seek 60 秒后的稳定片段实际约在原曲 53 秒，seek 120 秒后实际约在 116 秒，seek 180 秒后实际约在 183 秒。其报告时长 223.538503 秒也不同于源文件有效时长。已将范围缩小到实际 Kotlin 缓存桥接路径，继续检查媒体类型与供数行为。
- 诊断使用只读缓存存储代理，所有写入/删除仅被忽略，不更改用户缓存。临时 Debug 诊断代码与用户音频不作为产品代码提交，排查结束需移除入口并恢复普通应用。

### 精确索引参数桥接根因

进一步单变量对照排除了缓存读取、分段供数和媒体信息提交时机。保持同一个 Kotlin 资源加载委托，仅用 Swift 创建 AVURLAsset，`providesPreciseDurationAndTiming` 从 false 变为 true，真实歌曲定位恢复。

自生成 562 字节 FLAC 回归（160123 个 8 kHz 单声道静音采样，短尾帧）进一步定位：

| 参数字典 | 系统实际精确索引 | 报告时长 |
| --- | --- | --- |
| Kotlin Map，值为 true / NSNumber(bool=true) / numberWithBool(true) | false | 20.480 秒（含尾帧填充） |
| Foundation 创建并原样传回的原生字典 | true | 20.015375 秒（真实有效采样） |

Kotlin Map 中的布尔值在此调用链中被桥接为 KotlinBoolean，AVFoundation 未将其识别为原生精确索引选项。显式 NSNumber 和改用 AVURLAsset 类工厂均不能解决。新增 `preciseAudioAsset`：仅初始化一次 Foundation 原生字典并保留 NSDictionaryAsKMap，不做 toMap 复制；缓存入口与普通 URL 入口共用。保留零 seek 容差，不改歌词时间戳，不加固定/比例补偿，不更改通用缓存数据结构。

新增真实 FLAC 有效时长/精确索引状态断言及完整缓存离线重开回归。70 项原生/播放核心测试通过。修复版真实歌曲真机音频对照因本次系统拒绝开发包启动而暂未完成，正在核查签名；不能将上轮残留的诊断文件当作修复后结果。

最终生产代码已移除 Swift/Kotlin 临时诊断入口和测试歌曲硬编码；再次运行 70 项原生回归，零失败。用户截图确认真机阻塞是联网验证开发者证书，非应用代码启动崩溃。设备原有曲库和缓存未清除；本轮生成的 tmp 诊断样本暂留，恢复验证后清理。签名证书、描述文件有效期及本地深度签名校验通过，全新构建也被设备相同验证状态拦截。

已保留数据覆盖安装移除诊断入口后的普通 Debug 修复包（构建与签名校验成功）。当前启动仍被设备开发者联网验证阻止，真实歌曲修复后声音对照与用户体验验收仍待完成；未发布新的 TestFlight 版本。

### 2026-10-29 启动后无响应跟进

- 用户关闭 Wi-Fi 后开发包验证成功，确认此前启动拦截与联网验证有关；并非缺少开发者模式。
- 真机 13:58:14 / 13:58:25 的两份系统报告均为 `0x8BADF00D`，应用退出时主线程无法响应。第一份栈停在 Coil 错误日志的 Kotlin/Native 堆栈符号化；第二份停在 AVPlayerItem 媒体选择通知同步等待，后台同时等待媒体服务时长读取。
- 将 IosCachedAsset 的资源委托、请求状态以及协程恢复统一放到独立串行 dispatch queue，避免主线程等待 AVFoundation 时字节响应也依赖主线程。关闭时同队列清理请求；保留精确时长与零容差 seek。
- Coil 对 CoverUnavailableException 不再重复打印异常堆栈，源读取端的简短超时日志仍保留，其他错误仍正常记录。
- 原生回归 70 项通过；缓存 WAV / FLAC 离线重开测试增加刻意阻塞主线程时仍能完成资源准备的断言，并继续验证精确 seek、播放和暂停。日志：`/tmp/lmusic-hang-regression-tests.log`。
- 真机新包验证待完成；上述等待链的修复仍需在用户原始曲库验证。

真机修复包已构建成功（`/tmp/lmusic-hang-fix-build.log`）；14:06 覆盖安装因无线调试连接超时失败（Network.NWError 60），不能视为已安装。已请求 USB 连接并解锁，后续继续保留数据安装与验证。

14:09 用户解锁后重试覆盖安装成功，数据保留；`devicectl --console` 启动成功，进程 PID 63870。证据：`/tmp/lmusic-hang-fixed-install-retry.log`、`/tmp/lmusic-hang-fixed-launch.log`。尚需真机交互与 WebDAV 歌词同步体验确认，进程存在不等同于界面无卡顿。
