@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.makeSureAllSourcesJarAfterKsp
import com.lalilu.gradle.setupIOSTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.lalilu.llyric"
version = "1.0.0"

kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    setupIOSTarget {
    }
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.io)
                api(libs.kotlinx.serialization)
                api(libs.xmlutil.core)
                api(libs.xmlutil.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
                implementation("org.junit.jupiter:junit-jupiter-engine:5.13.4")
                implementation("org.junit.platform:junit-platform-launcher:1.13.4")
            }
        }
    }
}

android {
    namespace = group.toString()
    compileSdk = libs.versions.android.targetSdk.get().toInt()

    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }
}

makeSureAllSourcesJarAfterKsp()