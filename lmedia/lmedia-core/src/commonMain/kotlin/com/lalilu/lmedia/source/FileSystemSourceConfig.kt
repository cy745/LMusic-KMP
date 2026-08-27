package com.lalilu.lmedia.source

import kotlinx.serialization.Serializable

/** 文件系统数据源的持久化配置。目录使用 FileKit bookmark 保存跨启动访问凭据。 */
@Serializable
data class FileSystemSourceConfig(
    val directoryBookmark: String = "",
)
