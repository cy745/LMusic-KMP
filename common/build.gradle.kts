@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.lalilu.common"
version = "1.0.0"

kotlin {
    jvm()
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = group.toString()
        compileSdk = libs.versions.android.targetSdk.get().toInt()

        compilations.configureEach {
            compilerOptions.configure {
                jvmTarget = JvmTarget.JVM_11
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.runtime)
                api(libs.kermit)
                api(libs.krouter.core)
                api(libs.kotlinx.serialization)
                api(libs.koin.core)
                api(libs.koin.annotations)
                api(libs.kermit)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val androidMain by getting {
            dependencies {
                api("com.blankj:utilcodex:1.31.1")
            }
        }
    }
}