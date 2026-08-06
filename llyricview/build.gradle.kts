@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupMultiplatform
import com.lalilu.gradle.setupKoin
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
    alias(libs.plugins.osdetector)
    alias(libs.plugins.stability.analyzer)
}

group = "com.lalilu.llyricview"
version = "1.0.0"
extra.set("artifactId", "core")

kotlin {
    setupMultiplatform()
    setupKoin()

    sourceSets {
        commonMain.dependencies {
            api(project(":component"))
            api(project(":llyric"))
            api(project(":lfont"))
            api(libs.compose.resources)
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.filekit.core)
            api(libs.bundles.settings)
        }
        commonTest.dependencies {
            api(libs.kotlin.test)
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

setupPublish()
