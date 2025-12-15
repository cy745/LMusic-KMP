@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.XcodeDetector
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.component"
version = "1.0.0"

kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            // jvmTarget = JvmTarget.JVM_11
        }
    }
    XcodeDetector.whenXcodeInstalled {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        )
    }
    wasmJs {
        browser()
        binaries.executable()
    }

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

                api(libs.compose.adaptive)
                api(libs.compose.ui.backhandler)

                api(libs.jbx.navigation3.ui)
                api(libs.androidx.navigation3.runtime)
                implementation("androidx.collection:collection:1.5.0")

                api(libs.bundles.jbx)
                api(libs.bundles.coil)
                api(libs.bundles.settings)
                api(libs.bundles.flowmvi)
                api(libs.koin.compose)
                api(libs.koin.compose.viewmodel)
                api(libs.remixicon.kmp)
                api(libs.qrcode.kotlin)
                api(libs.sonner)
                api(libs.materialKolor)
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
            }
        }
    }
}

android {
    namespace = group.toString()
    compileSdk = libs.versions.android.targetSdk.get().toInt()
}