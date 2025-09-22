@file:OptIn(ExperimentalWasmDsl::class)

import com.google.devtools.ksp.gradle.KspAATask
import com.lalilu.gradle.XcodeDetector
import com.lalilu.gradle.makeSureAllSourcesJarAfterKsp
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    XcodeDetector.whenXcodeInstalled {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach {
            it.compilations.getByName("main") {
                cinterops {
                    create("MusicKitWrapper")
                    create("Taglib") {
                        definitionFile.set(file("src/nativeInterop/taglib/Taglib.def"))
                        headers(file("src/nativeInterop/taglib/include/taglib/tag_c.h"))
                    }
                }
            }
        }
    }
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.components.resources)
                api(project(":component"))
                api(libs.koin.core)
                api(libs.koin.annotations)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.io)
                api(libs.filekit.core)
                api(libs.filekit.dialogs)
                api(libs.filekit.dialogs.compose)
                api(libs.bundles.settings)
                api(libs.ktor.server.core)
                api(libs.ktor.server.cors)
                api(libs.ktorfit)
                api(kotlincrypto.hash.md)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        iosMain.dependencies {
            implementation(libs.ktor.server.cio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.server.netty)
            implementation(compose.preview)
        }
        jvmMain.dependencies {
            implementation(libs.native.lib.loader)
            implementation(libs.ktor.server.netty)
        }
        wasmJsMain.dependencies {
            implementation(npm("taglib-wasm", "0.5.4"))
        }
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

dependencies {
    debugImplementation(libs.compose.ui.tooling)
    kspCommonMainMetadata(libs.koin.compiler)
}

android {
    namespace = group.toString()
    compileSdk = libs.versions.android.targetSdk.get().toInt()

    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }
}

makeSureAllSourcesJarAfterKsp()

project.tasks.withType(KspAATask::class.java).configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        if (name == "kspDebugKotlinAndroid") {
            enabled = false
        }
        if (name == "kspReleaseKotlinAndroid") {
            enabled = false
        }
        if (name == "kspKotlinIosSimulatorArm64") {
            enabled = false
        }
        if (name == "kspKotlinIosX64") {
            enabled = false
        }
        if (name == "kspKotlinIosArm64") {
            enabled = false
        }
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

mavenPublishing {
    coordinates(
        groupId = group.toString(),
        version = version.toString(),
        artifactId = "core",
    )

    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGenerate"),
            sourcesJar = true,
        )
    )

    pom {
        name = "LMedia"
        description = "LMedia"
        inceptionYear = "2025"
    }

    publishToMavenCentral(true)
//    signAllPublications()
}