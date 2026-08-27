package com.lalilu.lmedia.source

import kotlinx.serialization.Serializable

/**
 * Remote 数据源的持久化配置。
 *
 * 原始密码只用于本地生成认证信息，不写入持久化存储。
 */
@Serializable
data class RemoteSourceConfig(
    val url: String = "",
    val salt: String = "",
    val token: String = "",
) {
    val isConfigured: Boolean
        get() = url.isNotBlank()
}
