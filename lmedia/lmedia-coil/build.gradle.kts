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

val artifactId = "coil"
group = "com.lalilu.lmedia"
version = "1.0.0"

kotlin {
    jvm()
    androidTarget {
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
                api(project(":component"))
                api(project(":lmedia:lmedia-core"))
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