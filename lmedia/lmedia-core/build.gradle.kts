@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.*
import com.lalilu.gradle.XcodeDetector
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
    alias(libs.plugins.swiftklib)
    alias(libs.plugins.osdetector)
    alias(libs.plugins.ktorfit)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "core")

applyMultiplatform(configureBlock = {
    targets.filter { it.name.startsWith("ios") }
        .forEach {
            it.compilations.getByName<KotlinNativeCompilation>("main") {
                cinterops {
                    create("MusicKitWrapper")
                    create("Taglib") {
                        definitionFile.set(file("src/nativeInterop/taglib/Taglib.def"))
                        headers(file("src/nativeInterop/taglib/include/taglib/tag_c.h"))
                    }
                }
            }
        }
}) {
    main.dependencies {
        api(project(":common"))
        api(libs.bundles.flowmvi)
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
    test.dependencies {
        implementation(libs.kotlin.test)
    }
    jvmMain.dependencies {
        api(libs.native.lib.loader)
    }
    wasmJsMain.dependencies {
        implementation(npm("taglib-wasm", "0.5.4"))
    }
}

XcodeDetector.whenXcodeInstalled {
    swiftklib {
        create("MusicKitWrapper") {
            path = file("src/nativeInterop/MusicKitWrapper")
            packageName("com.lalilu.lmedia")
        }
    }
}