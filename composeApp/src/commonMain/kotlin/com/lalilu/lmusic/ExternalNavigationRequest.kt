package com.lalilu.lmusic

/**
 * 平台入口传入的外部页面跳转请求。
 *
 * [route] 和 [params] 继续交给应用现有的 KRouter 解析，外部入口不维护第二套路由表；[id]
 * 用于区分内容完全相同的连续请求，确保重复打开同一 Deep Link 时仍会触发跳转。
 */
data class ExternalNavigationRequest(
    val id: Long,
    val route: String,
    val params: Map<String, Any?> = emptyMap(),
)
