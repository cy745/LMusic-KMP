package com.lalilu.lmedia.net

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 后台传输门控规则：只有 Wi-Fi 与以太网放行。
 *
 * 这条规则决定了"会不会花用户流量"，所以每个取值都要有一条断言钉住——尤其是未知网络必须
 * 按暂停处理，否则一旦平台拿不到类型，就会在计费网络上下载整个音乐库。
 */
class NetworkGateTest {

    @Test
    fun `only wifi and ethernet allow background transfers`() {
        assertTrue(NetworkType.WIFI.allowsBackgroundTransfer)
        assertTrue(NetworkType.ETHERNET.allowsBackgroundTransfer)
    }

    @Test
    fun `metered and unknown networks stay paused`() {
        assertFalse(NetworkType.MOBILE.allowsBackgroundTransfer, "移动网络会真金白银地花流量")
        assertFalse(NetworkType.OTHER.allowsBackgroundTransfer, "蓝牙共享按计费网络处理")
        assertFalse(
            NetworkType.UNKNOWN.allowsBackgroundTransfer,
            "拿不到网络类型时保守暂停：宁可等一次网络变化事件",
        )
    }
}
