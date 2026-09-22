package com.lalilu.lmedia.stream

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer

/**
 * 建一个回环代理用的 Ktor 服务。
 *
 * 为什么不做成"把引擎工厂当参数传进来"：`embeddedServer` 的工厂带两个泛型参数
 * （`ApplicationEngineFactory<TEngine, TConfiguration>`），星投影之后推不出类型，
 * 只能把"整个创建动作"抬成 expect/actual。
 *
 * ⚠️ 这个分平台不是随意的，它修的是一个实测到的性能问题（issue #30）：
 *
 * **JVM/Desktop 用 Netty**：CIO 的写路径在"写阻塞"（客户端读得比我们发得慢、或者暂停不读）
 * 时是在 select 上忙等 —— 实测该线程 **8s 里吃满 8.06s CPU，而进程写传输是 0.00 MB/s**
 * （在转，不是在发数据；暂停时 100%、播放时也常年满）。回环代理天然就是"我们发得比播得快"，
 * 所以这个忙等几乎覆盖整个播放时段，整进程常年烧 1.4~1.7 个核。
 * Netty 的事件循环在不可写时是注册兴趣等事件，不会忙等。
 * （`ktor-network-jvm:3.6.0` 的 `CIOWriter.kt` 与 3.3.3 逐字节相同 → 升级 Ktor 修不掉。）
 *
 * **Android / iOS / Web 仍用 CIO**：跟改动前完全一致，行为不变。
 * 这几个平台没有实测过 Netty 路径，不动它们。
 */
internal expect fun createProxyServer(
    host: String,
    port: Int,
    module: Application.() -> Unit,
): EmbeddedServer<*, *>
