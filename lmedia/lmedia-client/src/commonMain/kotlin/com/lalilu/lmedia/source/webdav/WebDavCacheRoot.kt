package com.lalilu.lmedia.source.webdav

import com.lalilu.lmedia.platformCacheDirectory
import org.koin.core.annotation.Single

/**
 * 提供 WebDAV 缓存根目录；返回 null 表示当前平台没有可用的文件系统（Web）。
 *
 * 抽成可注入的接口有两个好处：测试不必初始化平台文件系统（JVM 上 FileKit 需要 `FileKit.init`，
 * 而那是 JVM 专属 API，commonTest 访问不到），"平台不支持"这条分支也能被确定性地验证。
 */
fun interface WebDavCacheRootProvider {
    fun cacheRoot(): String?
}

@Single(binds = [WebDavCacheRootProvider::class])
class PlatformWebDavCacheRootProvider : WebDavCacheRootProvider {
    override fun cacheRoot(): String? = platformCacheDirectory()
}
