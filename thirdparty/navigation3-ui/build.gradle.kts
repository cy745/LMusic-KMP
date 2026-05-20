@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
    androidTarget {}

    jvm("desktop")

    js {
        browser()
        binaries.executable()
    }
    wasmJs {
        browser()
        binaries.executable()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    listOf(
        macosArm64()
    )

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.androidx.navigation3.runtime)
                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.jbx.navigationevent.compose)
                api(libs.jbx.lifecycle.runtime.compose)

                api("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
                api("androidx.collection:collection:1.5.0")
            }
        }
    }
}


android {
    compileSdk = libs.versions.android.targetSdk.get().toInt()
    namespace = "org.jetbrains.androidx.navigation3"
}