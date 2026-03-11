@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.applyMultiplatform
import com.lalilu.main
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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

applyMultiplatform {
    main.dependencies {
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