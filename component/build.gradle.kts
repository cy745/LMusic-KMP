@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.dsl.androidLibrary
import com.lalilu.gradle.XcodeDetector
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.lalilu.component"
version = "1.0.0"

kotlin {
    jvm()
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = group.toString()
        compileSdk = libs.versions.android.targetSdk.get().toInt()

        compilations.configureEach {
            compileTaskProvider.configure {
                (compilerOptions as? KotlinJvmCompilerOptions)
                    ?.jvmTarget = JvmTarget.JVM_11
            }
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
                api(compose.components.resources)
                api(compose.components.uiToolingPreview)
                api(libs.bundles.jbx)
                api(libs.compose.adaptive)
                api(libs.vortex)
                api(libs.vortex.koin)
                api(libs.bundles.coil)
                api(libs.bundles.settings)
                api(libs.koin.compose)
                api(libs.remixicon.kmp)
                api(libs.qrcode.kotlin)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.coil.gif)
            }
        }
    }
}