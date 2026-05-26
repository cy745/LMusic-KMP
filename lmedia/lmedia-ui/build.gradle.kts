@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupMultiplatform
import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupSweetSpi
import com.lalilu.gradle.setupPublish
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "ui")

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

kotlin {
    setupMultiplatform()
    setupKoin()
    setupSweetSpi()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.resources)
            api(project(":component"))
            api(project(":lmedia:lmedia-core"))
            api(project(":lmedia:lmedia-server"))
            api(project(":lmedia:lmedia-client"))
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.filekit.core)
            api(libs.filekit.dialogs)
            api(libs.filekit.dialogs.compose)
            api(libs.bundles.settings)
            api(kotlincrypto.hash.md)
        }
    }
}

setupPublish()
