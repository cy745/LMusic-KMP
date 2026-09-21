package com.lalilu.lmedia.net

import dev.tmapps.konnection.Konnection
import dev.tmapps.konnection.NetworkConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Android / JVM / iOS 的实现：转发 konnection 的网络类型流。
 *
 * konnection 内部是 `MutableStateFlow`，订阅后立刻给出当前类型；拿不到实例或流内部报错时退化为
 * [NetworkType.UNKNOWN]（按门控规则即暂停后台传输），而不是把异常抛给调用方——网络门控失败
 * 不该让后台任务崩掉。
 */
actual fun observePlatformNetworkType(): Flow<NetworkType> {
    val konnection = runCatching { Konnection.instance }.getOrNull()
        ?: return flowOf(NetworkType.UNKNOWN)
    return konnection.observeNetworkConnection()
        .map { it.toNetworkType() }
        .catch { emit(NetworkType.UNKNOWN) }
}

/** konnection 的连接类型 → 本项目只关心"能否后台传输"的类型。 */
internal fun NetworkConnection?.toNetworkType(): NetworkType = when (this) {
    NetworkConnection.WIFI -> NetworkType.WIFI
    NetworkConnection.ETHERNET -> NetworkType.ETHERNET
    NetworkConnection.MOBILE -> NetworkType.MOBILE
    NetworkConnection.BLUETOOTH_TETHERING -> NetworkType.OTHER
    NetworkConnection.UNKNOWN_CONNECTION_TYPE, null -> NetworkType.UNKNOWN
}
