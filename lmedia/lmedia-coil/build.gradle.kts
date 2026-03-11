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
extra.set("artifactId", "coil")

applyMultiplatform {
    main.dependencies {
        api(project(":component"))
        api(project(":lmedia:lmedia-core"))
    }
}

ktorfit {
    compilerPluginVersion.set("2.3.3")
}