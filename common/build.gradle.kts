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

group = "com.lalilu.common"
version = "1.0.0"

kotlin {
    setupMultiplatform()
    setupKoin()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.ui)
            api(libs.compose.runtime)
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
            api(kotlincrypto.hash.md)
            api(libs.diff.utils)
            api(libs.room3.common)
            api(libs.paging.common)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
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
        val webMain by creating {
            dependencies {
                api(libs.ktor.client.js)
                api(libs.kotlinx.browser)
            }
        }
    }
}
