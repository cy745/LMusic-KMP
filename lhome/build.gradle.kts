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

group = "com.lalilu.lhome"
version = "1.0.0"

kotlin {
    setupMultiplatform()
    setupKoin()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":component"))
                implementation(project(":lmedia:lmedia-core"))
                implementation(project(":lmedia:lmedia-data"))
                implementation(project(":lmedia:lmedia-ui"))
                implementation(project(":lplayer"))
                implementation(libs.remixicon.kmp)
                implementation(libs.compose.resources)
                implementation(libs.compose.preview)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
