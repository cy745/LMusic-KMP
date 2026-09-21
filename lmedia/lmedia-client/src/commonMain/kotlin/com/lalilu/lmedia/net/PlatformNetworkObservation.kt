package com.lalilu.lmedia.net

import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

/**
 * 生产环境的网络观测：转发平台网络类型流。
 *
 * 通过接口 + Koin 注入而不是让数据源直接调用 `expect` 函数，是为了让测试能喂任意网络序列
 * （Wi-Fi → 移动网络 → Wi-Fi）覆盖门控逻辑，不必真的切网络。
 */
@Single(binds = [NetworkObservation::class])
class PlatformNetworkObservation : NetworkObservation {
    override fun observe(): Flow<NetworkType> = observePlatformNetworkType()
}
