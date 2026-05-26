@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupMultiplatform
import com.lalilu.gradle.setupPublish
import com.lalilu.gradle.setupSweetSpi
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
extra.set("artifactId", "coil")

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

kotlin {
    setupMultiplatform()
    setupKoin()
    setupSweetSpi()

    sourceSets {
        commonMain.dependencies {
            api(project(":component"))
            api(project(":lmedia:lmedia-core"))
        }
    }
}

setupPublish()
