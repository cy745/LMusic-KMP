@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.*
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.common"
version = "1.0.0"

applyMultiplatform {
    main.dependencies {
        api(libs.compose.ui)
        api(libs.compose.runtime)
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
        api(libs.sweetspi.runtime)

        api(libs.room3.common)
        api(libs.paging.common)
    }
    test.dependencies {
        implementation(libs.kotlin.test)
    }
    androidMain.dependencies {
        api("com.blankj:utilcodex:1.31.1")
        api(libs.ktor.client.okhttp)
        api(libs.logback)
    }
    jvmMain.dependencies {
        api(libs.ktor.client.okhttp)
        api(libs.logback)
    }
    iosMain?.dependencies {
        api(libs.ktor.client.darwin)
    }
    val webMain by creating {
        dependencies {
            api(libs.ktor.client.js)
            api(libs.kotlinx.browser)
        }
    }
}