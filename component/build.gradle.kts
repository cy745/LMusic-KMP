@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.component"
version = "1.0.0"

kotlin {
    setupMultiplatform()
    setupKoin()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":common"))
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.ui)
                api(compose.preview)
                api(compose.components.resources)
                api(libs.compose.material)

                api(libs.compose.adaptive)
                api(libs.compose.ui.backhandler)

                api(libs.jbx.navigation3.ui)
                api(libs.androidx.navigation3.runtime)
                implementation("androidx.collection:collection:1.5.0")

                api(libs.bundles.jbx)
                api(libs.bundles.coil)
                api(libs.bundles.settings)
                api(libs.koin.compose)
                api(libs.koin.compose.viewmodel)
                api(libs.remixicon.kmp)
                api(libs.qrcode.kotlin)
                // compose-sonner 发布时把发布者平台(macos-arm64)的 Compose Desktop 构件写进了
                // 元数据(scope=runtime)，于是所有宿主都被塞进 macOS 的 Skiko 原生库：非 macOS 上
                // Compose 测试会因加载不到本平台原生库而失败，打包时也会多带一份无用的原生库。
                // 宿主平台那一份由各自的 compose.desktop.currentOs 提供，这里只剔除被焊死的那个平台。
                // 说明：KMP source-set 的 api(...) 带 lambda 的重载只接受 String，而版本目录给出的
                // MinimalExternalModuleDependency 是只读的，故按「组:模块:版本」记法声明，
                // 取值仍来自版本目录，保持单一事实来源。
                api("${libs.sonner.get().module}:${libs.sonner.get().versionConstraint.requiredVersion}") {
                    exclude(group = "org.jetbrains.compose.desktop", module = "desktop-jvm-macos-arm64")
                }
                api(libs.materialKolor)
                api(libs.reorderable)
                api(libs.paging.compose)

                api(libs.room3.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val androidMain by getting {
            dependencies {
                api(compose.preview)
                api(compose.uiTooling)
                api(libs.coil.gif)
                api(libs.sqlite.bundled)
            }
        }

        val jvmMain by getting {
            dependencies {
                api(libs.sqlite.bundled)
            }
        }

        iosMain.dependencies {
            api(libs.sqlite.bundled)
        }

        webMain.dependencies {
            api(libs.sqlite.web)
        }
    }
}
