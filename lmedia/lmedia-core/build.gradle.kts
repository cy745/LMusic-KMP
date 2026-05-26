@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupMultiplatform
import com.lalilu.gradle.setupPublish
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
    alias(libs.plugins.osdetector)
    alias(libs.plugins.ktorfit)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "core")

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

kotlin {
    setupMultiplatform(
        setupIosTarget = {
            forEach {
                it.compilations.getByName<KotlinNativeCompilation>("main") {
                    val musicKitWrapper by cinterops.creating
                    musicKitWrapper.apply {
                        definitionFile.set(file("src/nativeInterop/MusicKitWrapper/MusicKitWrapper.def"))
                    }

                    val taglib by cinterops.creating
                    taglib.apply {
                        definitionFile.set(file("src/nativeInterop/taglib/Taglib.def"))
                        headers(file("src/nativeInterop/taglib/include/taglib/tag_c.h"))
                    }
                }
            }
        }
    )
    setupKoin()

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(libs.compose.ui)
            api(libs.compose.resources)
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
        androidMain.dependencies {
            api(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            api(libs.native.lib.loader)
        }
        wasmJsMain.dependencies {
            implementation(npm("taglib-wasm", "0.5.4"))
        }
    }
}

compose {
    resources {
        publicResClass = true
    }
}

setupPublish()
