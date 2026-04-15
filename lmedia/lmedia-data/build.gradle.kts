@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.*
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "data")

applyMultiplatform {
    main.dependencies {
        api(project(":lmedia:lmedia-core"))
        api(libs.koin.core)
        api(libs.koin.annotations)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.io)
        api(libs.room3.runtime)
    }
    test.dependencies {
        api(libs.kotlin.test)
        api(libs.kotlinx.coroutines.test)
    }
    androidMain.dependencies {
        api(libs.androidx.core.ktx)
        api(libs.androidx.test.ktx)
    }
}