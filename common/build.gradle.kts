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

group = "com.lalilu.common"
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
                api(compose.runtime)
                api(libs.kermit)
                api(libs.kotlin.logging)
                api(libs.human.readable)
                api(libs.krouter.core)
                api(libs.kotlinx.serialization)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
                api(libs.koin.core)
                api(libs.koin.annotations)

                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.client.serialization)
                api(libs.ktor.serialization.json)
                api(libs.ktor.client.logging)
                api(libs.ktorfit)

                // kotlin crypto
                api(kotlincrypto.hash.md)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        androidMain.dependencies {
            api("com.blankj:utilcodex:1.31.1")
            api(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            api(libs.ktor.client.okhttp)
            api("ch.qos.logback:logback-classic:1.5.18")
        }
        iosMain.dependencies {
            api(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            api(libs.ktor.client.js)
        }
    }
}