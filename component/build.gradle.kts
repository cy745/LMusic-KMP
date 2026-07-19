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
                api(libs.sonner)
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

        val webMain by creating {
            dependencies {
                api(libs.sqlite.web)
            }
        }
    }
}
