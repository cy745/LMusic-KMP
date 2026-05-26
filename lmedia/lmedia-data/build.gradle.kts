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
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "data")

kotlin {
    setupMultiplatform()
    setupKoin()
    setupSweetSpi()

    sourceSets {
        commonMain.dependencies {
            api(project(":lmedia:lmedia-core"))
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.room3.runtime)
        }
        commonTest.dependencies {
            api(libs.kotlin.test)
            api(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            api(libs.androidx.core.ktx)
            api(libs.androidx.test.ktx)
        }
    }
}

setupPublish()
