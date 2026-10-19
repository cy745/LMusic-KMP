# 在线缓存通用化：落地与验收计划

目标：把「回环代理 + 读穿缓存」从 WebDAV 数据源里摘出来，做成所有在线数据源都能零成本接入的通用能力；
顺带修掉整文件下载阻塞导致的播放超时、歌词列表不滚动、封面过渡立即失败三个问题。

设计总览见 `docs/online-cache-refactor.html`。本文只写**拆解与验收**。

## 分阶段

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| P0 已完成 | 领域契约：`MediaUrlRewriter`、`MediaDataSource` 的 `originHeaders` / `cacheKey` / `cacheTotalSize`；`MediaContentResolver` 在解析播放地址时单点改写 | 域模块单测通过；未注册改写器时行为与改造前完全一致 |
| P1 已完成 | 通用缓存包 `com.lalilu.lmedia.cache`：`RangeHeader` / `MediaCacheStore` / `LoopbackRangeProxy` 迁出 WebDAV 包，去掉 WebDAV 命名与固定 namespace | `:lmedia:lmedia-client:jvmTest` 全绿；代理用例只依赖 `RangeUpstream` 接口 |
| P2 | 通用缓存控制器：`MediaCacheRootProvider`、`CachedOrigin`、`HttpRangeUpstream`、`MediaCacheController`、`CacheUrlRewriter`；WebDAV 只保留 `cacheKey` / `originHeaders` / `cacheTotalSize` 与提取队列 | 任意 http(s) 数据源只要实现三个小方法就能拿到回环地址与离线缓存；WebDAV 的 `getMedia` 返回原始地址，回环改写只发生在 `resolveMediaData` |
| P3（暂缓） | 通用元数据提取：`MediaExtractionService` + `ExtractedMetadataStore` + 三条发布通道 | 触发点已经通用（控制器的进度监听），但提取实现仍在 webdav 包：目前只有它需要"从字节里猜标签"，别家源在 API 里就带着完整元数据。等第二个源需要时再抽 |
| P4（已完成） | 播放请求抢占后台预取；未知长度透传也串行化 | `:lmedia:lmedia-client:jvmTest` 覆盖"预取卡住时播放立刻拿到字节"；慢速 NAS 上不再出现读超时 |
| P5（已完成） | 歌词跟随重构 + 封面失败窗口内重试 | 列表在时间前进/切歌后都真的滚到当前行；封面首次失败不再立刻切占位，10 秒窗口内重试；`stabilityCheck` 通过 |

### P4 备注：为什么先做抢占而不是分段缓存

分段缓存（按固定块存、按块并发）能进一步解决"跳到未缓存位置要从头补"，但它会破坏
**连续前缀**这个前提：`taglib` 只认本地文件，30% / 100% 两次提取直接读 `<key>.part` 的前缀字节；
改成离散分块就得先拼一个临时文件再读，代价与复杂度都不小。而线上真正致命的是"整首预取占着锁
把播放饿死"，抢占能把这条彻底解决，代价只有几行状态管理。分段留给后续单独评估。

## P2 详细清单

1. 领域：`MediaUrlRewriter.rewrite` 改为 `suspend`（首帧需要启动代理）；`MediaDataSource.cacheTotalSize`（null = 未知，只能整文件透传缓存）。
2. `cache/MediaCacheRootProvider`：从 `WebDavCacheRootProvider` 泛化，平台实现仍走 `platformCacheDirectory()`。
3. `cache/CachedOrigin`：`url` + `headers` + `totalSize` + `contentType`，是代理真正需要的上游描述。
4. `cache/HttpRangeUpstream`：按 key 注册上游，`GET` + `Range` 拉取；`start > 0` 却返回 200 时直接失败（说明上游不支持 Range，继续写缓存会污染前缀）。
5. `cache/MediaCacheController`：单例持有 store + proxy + upstream；对外只暴露 `register` / `progress` / `filledSize` / `localPath` / `ensureCached` / `addProgressListener`。
6. `cache/CacheUrlRewriter`：`MediaUrlRewriter` 实现，按 `audios.mediaSourceName` 找回数据源，拼出 `CachedOrigin` 后注册并返回回环地址。
7. WebDAV：删掉自己的 proxy / cache / `ProxyBackend` / `fetchRange`；播放地址交回原始地址，缓存与代理由通用层负责。
8. 测试：`WebDavSourceTest` 的「播放走回环」改为断言 `CacheUrlRewriter` 的输出；设备端用例同样改为先改写再请求。

## 验收方式

- 单测：`./gradlew :lmedia:lmedia-client:jvmTest :lmedia:lmedia-domain:jvmTest :lplayer:jvmTest`
- 稳定性：`./gradlew :lplayer:stabilityCheck`
- 真机：接本机 WebDAV（docker `lmusic-webdav`，`adb reverse tcp:8080`），验证播放、拖动、切歌、断网续播与离线缓存命中
- Koin 图：`./gradlew :composeApp:jvmTest --tests com.lalilu.lmusic.KoinModulesTest`（新增的控制器与改写器必须能独立装配）
