@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupMultiplatform
import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupPublish
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "client")

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

kotlin {
    setupMultiplatform(
        setupAndroidTarget = {
            // 真实设备/模拟器上的端到端用例：回环代理 + 读穿缓存 + taglib 提取在真机 Android
            // 运行时上的行为（JVM 单测覆盖不到 FileKit 缓存目录与 taglib JNI 绑定）。
            withDeviceTest {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                multidex.enable = true
            }
        },
    )
    setupKoin()

    sourceSets {
        commonMain.dependencies {
            api(project(":lmedia:lmedia-core"))
            api(project(":common"))
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.filekit.core)
            api(libs.bundles.settings)
            api(libs.ktor.server.core)
            api(libs.ktor.server.cors)
            api(libs.ktor.server.cio)
            api(libs.ktor.server.content.negotiation)
            api(libs.ktorfit)
            api(libs.xmlutil.core)
            api(kotlincrypto.hash.md)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test.ext:junit:1.2.1")
                // 断言用 kotlin.test（与其它源集一致；参数顺序是 value 在前）
                implementation(libs.kotlin.test)
                // 真实播放链路：用真实 Media3/ExoPlayer 播代理地址并 seek
                implementation(libs.media3.exoplayer)
            }
        }
    }
}

setupPublish()
