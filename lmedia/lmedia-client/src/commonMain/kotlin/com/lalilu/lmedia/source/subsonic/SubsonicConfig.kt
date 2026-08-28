package com.lalilu.lmedia.source.subsonic

import kotlinx.serialization.Serializable

/**
 * Subsonic 配置
 * 密码不保存在本地，也不发送至云端，与salt计算生成token，使用token进行鉴权
 *
 * @property url 服务器地址
 * @property username 用户名
 * @property salt 密码盐
 * @property token 令牌
 * @property client 客户端名称
 * @property version 版本
 * @property format 响应格式
 */
@Serializable
data class SubsonicConfig(
    val url: String = "",
    val username: String = "",
    val salt: String = "",
    val token: String = "",
    val client: String = "LMusic",
    val version: String = "6.1.4",
    val format: String = "json",
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() && username.isNotBlank() && salt.isNotBlank() && token.isNotBlank()

    companion object {
        val Empty = SubsonicConfig()
    }
}
