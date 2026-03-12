@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.applyMultiplatform
import com.lalilu.gradle.XcodeDetector
import com.lalilu.main
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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

applyMultiplatform {
    main.dependencies {
        api(libs.compose.resources)
        api(project(":component"))
        api(project(":lmedia:lmedia-core"))
        api(project(":lmedia:lmedia-server"))
        api(project(":lmedia:lmedia-client"))

//                api(libs.compose.adaptive)
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

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

dependencies {
    add("kspJvm", libs.room3.compiler)
    add("kspAndroid", libs.room3.compiler)
    add("kspWasmJs", libs.room3.compiler)
    XcodeDetector.whenXcodeInstalled {
        add("kspIosArm64", libs.room3.compiler)
        add("kspIosSimulatorArm64", libs.room3.compiler)
    }
}