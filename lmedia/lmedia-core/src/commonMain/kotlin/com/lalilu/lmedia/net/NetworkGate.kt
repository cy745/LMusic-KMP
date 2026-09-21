package com.lalilu.lmedia.net

import kotlinx.coroutines.flow.Flow

/**
 * 平台网络类型。
 *
 * 只区分"能不能在后台跑流量"这一类决策所需的粒度，不关心运营商、信号强度、IP 等信息——
 * 那些属于网络详情，而不是传输门控。
 */
enum class NetworkType {
    WIFI,
    ETHERNET,
    MOBILE,
    /** 蓝牙共享、VPN 之外的其它已连接类型等。 */
    OTHER,

    /** 拿不到类型（无连接、尚未收到首次回调、平台不支持）。 */
    UNKNOWN;

    /**
     * 是否允许后台传输（主动下载补全、元数据上传）。
     *
     * 只放行 Wi-Fi 与以太网：移动网络与蓝牙共享会实打实花用户流量，而音乐库补全动辄几百 MB。
     * [UNKNOWN] 按**保守暂停**处理——宁可等一次网络类型变化事件，也不要在可能计费的网络上下载整库。
     * 用户想立刻继续时由 UI 提供"手动继续"（见 `WebDavSource.allowMeteredTransfer()`）。
     */
    val allowsBackgroundTransfer: Boolean
        get() = this == WIFI || this == ETHERNET
}

/** 可注入的网络观测：测试喂任意序列即可覆盖门控逻辑，不必真的切网络。 */
fun interface NetworkObservation {
    fun observe(): Flow<NetworkType>
}

/**
 * 平台网络类型流（订阅后应立即给出当前值）。
 *
 * konnection 只发布 android / ios / jvm 变体，因此 Web 侧在 `wasmJsMain` 给出退化实现；
 * 这个 `expect` 放在 `lmedia-core` 是为了让依赖 konnection 的代码只存在于共享的非 Web 源集里，
 * 不把无 wasm 变体的依赖带进 commonMain。
 */
expect fun observePlatformNetworkType(): Flow<NetworkType>
