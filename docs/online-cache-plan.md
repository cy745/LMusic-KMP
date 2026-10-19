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
| P3 | 通用元数据提取：`MediaExtractionService` + `ExtractedMetadataStore` + 三条发布通道（快照归档 / `audioPatches` / `onExtracted`） | 提取不依赖 WebDAV 类型；缓存到 30% / 100% 的触发点由控制器上报 |
| P4 | 分段缓存与段级锁：整文件预取不再独占 key 锁；单段失败只影响该段 | 边下边播时拖动进度不触发 `SocketTimeoutException`；两个并发请求同一 key 不互相阻塞 |
| P5 | 两个 UI 问题的修复 + 文档/稳定性收尾 | 歌词列表在播放中自动跟随；切歌时旧封面保持到新封面就绪；`stabilityCheck` 通过 |

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
