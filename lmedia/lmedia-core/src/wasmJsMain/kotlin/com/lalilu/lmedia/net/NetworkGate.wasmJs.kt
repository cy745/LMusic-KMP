package com.lalilu.lmedia.net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Web 侧的退化实现：konnection 没有 wasm 变体，浏览器也无法可靠判断"当前网络是否计费"
 * （`Network Information API` 覆盖有限且只读）。
 *
 * 因此一律报 [NetworkType.UNKNOWN]，按门控规则即"保守暂停后台传输"。Web 上 WebDAV 数据源
 * 本身不可用（没有本地缓存目录、也起不了回环代理），这条路径不会影响真实用户。
 */
actual fun observePlatformNetworkType(): Flow<NetworkType> = flowOf(NetworkType.UNKNOWN)
