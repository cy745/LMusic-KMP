package com.lalilu.lmedia

/**
 * 平台私有的缓存目录绝对路径。
 *
 * Web 上没有可用的文件系统，返回 null；调用方（例如 WebDAV 数据源）据此判定该平台不支持需要
 * 本地缓存的来源。之所以做成 expect/actual 而不是直接用 FileKit：FileKit 的 wasmJs 变体并不提供
 * `cacheDir`/`filesDir`，在 commonMain 直接引用会让 Web 编译失败。
 */
expect fun platformCacheDirectory(): String?
