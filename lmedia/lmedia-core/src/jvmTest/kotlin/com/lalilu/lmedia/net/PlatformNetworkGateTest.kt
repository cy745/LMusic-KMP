package com.lalilu.lmedia.net

import dev.tmapps.konnection.NetworkConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 桌面端（JVM）的网络门控集成：konnection 用网卡名启发式判断类型，这里验证它确实能给出
 * 一个当前值（订阅即拿到），以及各连接类型到门控类型的映射。
 */
class PlatformNetworkGateTest {

    @Test
    fun `platform implementation reports the current network type`() = runBlocking {
        val type = withTimeout(10_000) { observePlatformNetworkType().first() }

        // 值本身取决于运行机器的网卡命名，这里只把结果打出来便于排查；
        // 关键是"能拿到当前值"——拿不到会让后台任务永远停在暂停态。
        println("desktop network type = $type")
        assertTrue(NetworkType.entries.contains(type))
    }

    @Test
    fun `maps konnection types to the gate types`() {
        assertEquals(NetworkType.WIFI, NetworkConnection.WIFI.toNetworkType())
        assertEquals(NetworkType.ETHERNET, NetworkConnection.ETHERNET.toNetworkType())
        assertEquals(NetworkType.MOBILE, NetworkConnection.MOBILE.toNetworkType())
        assertEquals(NetworkType.OTHER, NetworkConnection.BLUETOOTH_TETHERING.toNetworkType())
        assertEquals(NetworkType.UNKNOWN, NetworkConnection.UNKNOWN_CONNECTION_TYPE.toNetworkType())
        assertEquals(NetworkType.UNKNOWN, (null as NetworkConnection?).toNetworkType())

        // 映射之后仍要满足"只有 Wi-Fi/以太网放行"
        assertTrue(NetworkConnection.WIFI.toNetworkType().allowsBackgroundTransfer)
        assertFalse(NetworkConnection.MOBILE.toNetworkType().allowsBackgroundTransfer)
        assertFalse(NetworkConnection.BLUETOOTH_TETHERING.toNetworkType().allowsBackgroundTransfer)
    }
}
