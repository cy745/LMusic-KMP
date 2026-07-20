@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupMultiplatform
import com.lalilu.gradle.setupPublish
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.lplayer"
version = "1.0.0"
extra.set("artifactId", "core")

kotlin {
    setupMultiplatform(
        setupIosTarget = {
            forEach {
                it.compilations.getByName<KotlinNativeCompilation>("main") {
                    val observer by cinterops.creating
                    observer.apply {
                        definitionFile.set(file("src/nativeInterop/cinterop/observer.def"))
                    }
                }
            }
        }
    )
    setupKoin()

    sourceSets {
        commonMain.dependencies {
            api(project(":component"))
            api(project(":lmedia:lmedia-core"))
            api(project(":lmedia:lmedia-data"))
            api(project(":llyricview"))
            api(libs.compose.resources)
            api(libs.compose.preview)
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.filekit.core)
            api(libs.bundles.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.media3.session)
            implementation(libs.media3.exoplayer)
            implementation(libs.kotlinx.coroutines.guava)
            implementation(project(":lplayer:lib-decoder-flac"))
        }
        jvmMain.dependencies {
            implementation(libs.vlcj)
            implementation(libs.bundles.rococoa)
            implementation(project(":lplayer:libwrapper"))
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
