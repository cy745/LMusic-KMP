package com.lalilu.lmedia

/** Web 没有可用的文件系统：需要本地缓存的数据源在该平台不激活。 */
actual fun platformCacheDirectory(): String? = null
