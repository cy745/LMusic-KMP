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

group = "com.lalilu.lfont"
version = "1.0.0"

kotlin {
    setupMultiplatform()
    setupKoin()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":component"))
            implementation(libs.remixicon.kmp)
            implementation(libs.compose.resources)
            implementation(libs.compose.preview)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.kotlinx.io)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
