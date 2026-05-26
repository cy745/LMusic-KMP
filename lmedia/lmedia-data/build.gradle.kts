@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.XcodeDetector
import com.lalilu.gradle.applyPublish
import com.lalilu.gradle.commonMainKspDependencies
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "data")

kotlin {
    androidLibrary {
        namespace = "${group}.data"
        compileSdk = libs.versions.android.targetSdk.get().toInt()
    }
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask { enabled = false }
        }
        nodejs {
            testTask { enabled = false }
        }
        binaries.executable()
        binaries.library()
    }

    XcodeDetector.whenXcodeInstalled {
        listOf(
            iosArm64(),
            iosSimulatorArm64()
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":lmedia:lmedia-core"))
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.room3.runtime)
        }
        commonTest.dependencies {
            api(libs.kotlin.test)
            api(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            api(libs.androidx.core.ktx)
            api(libs.androidx.test.ktx)
        }
    }

    // Koin KSP
    commonMainKspDependencies {
        ksp(libs.koin.compiler)
    }
}

applyPublish()
