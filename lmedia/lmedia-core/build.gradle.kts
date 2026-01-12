@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.*
import com.lalilu.gradle.XcodeDetector
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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

// FIXME xcode的环境问题，直接在xcode中打包会出现报错，见https://github.com/ttypic/swift-klib-plugin/issues/65
// 需要在其他ide中执行过一次以后，才能在xcode中运行
tasks.named { it.startsWith("swiftklibMusicKitWrapper") || it.startsWith("cinteropMusicKitWrapper") }
    .forEach { it.enabled = System.getenv("PLATFORM_NAME") != "iphoneos" }