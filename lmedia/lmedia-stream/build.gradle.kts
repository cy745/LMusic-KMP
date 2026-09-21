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
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.kotlinx.serialization)
            api(libs.kermit)
            // 回环代理本体：把远端字节转成本机 HTTP 流
            api(libs.ktor.server.core)
            api(libs.ktor.server.cio)
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
