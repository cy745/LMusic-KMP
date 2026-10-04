@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "stream")

kotlin {
    setupMultiplatform()

    sourceSets {
        // 引擎实现按平台分：只有 JVM 用 Netty（避开 CIO 写阻塞时的忙等，见 ProxyServer.kt 与 issue #30），
        // Android / iOS / Web 共用这一份 CIO 实现，行为跟改动前一致。
        val nonJvmMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(nonJvmMain)
        wasmJsMain.get().dependsOn(nonJvmMain)
        nativeMain.get().dependsOn(nonJvmMain)

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.kotlinx.serialization)
            api(libs.kermit)
            // 回环代理本体：把远端字节转成本机 HTTP 流
            api(libs.ktor.server.core)
            api(libs.ktor.server.cio)
        }
        jvmMain.dependencies {
            // 桌面端改用 Netty 服务引擎：CIO 在写阻塞（客户端读得慢/停读）时会忙等跑满一个核
            api(libs.ktor.server.netty)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.core)
        }
    }
}
