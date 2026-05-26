@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupMultiplatform
import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupSweetSpi
import com.lalilu.gradle.setupPublish
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "client")

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

kotlin {
    setupMultiplatform()
    setupKoin()
    setupSweetSpi()

    sourceSets {
        commonMain.dependencies {
            api(project(":lmedia:lmedia-core"))
            api(project(":common"))
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.filekit.core)
            api(libs.bundles.settings)
            api(libs.ktor.server.core)
            api(libs.ktor.server.cors)
            api(libs.ktor.server.cio)
            api(libs.ktor.server.content.negotiation)
            api(libs.ktorfit)
            api(kotlincrypto.hash.md)
        }
    }
}

setupPublish()
