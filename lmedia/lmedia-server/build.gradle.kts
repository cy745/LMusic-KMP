@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.XcodeDetector
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

val artifactId = "server"
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
        )
    }
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":lmedia:lmedia-core"))
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
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        iosMain.dependencies {
        }
        androidMain.dependencies {
        }
        jvmMain.dependencies {
        }
        wasmJsMain.dependencies {
        }
    }
}

dependencies {
    add("kspJvm", libs.sweetspi.processor)
    add("kspAndroid", libs.sweetspi.processor)
    kspCommonMainMetadata(libs.sweetspi.processor)
    kspCommonMainMetadata(libs.koin.compiler)
}

android {
    namespace = "$group.$artifactId"
    compileSdk = libs.versions.android.targetSdk.get().toInt()

    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = group.toString(),
        version = version.toString(),
        artifactId = artifactId,
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