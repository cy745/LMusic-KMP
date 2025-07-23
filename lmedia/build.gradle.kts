@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
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
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":component"))
                api(libs.koin.core)
                api(libs.koin.annotations)
                api(libs.kotlinx.coroutines.core)
                api(libs.filekit.core)
                api(libs.filekit.dialogs)
                api(libs.filekit.dialogs.compose)
                api(compose.components.resources)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.native.lib.loader)
            }
        }
    }
}

dependencies {
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

tasks.getByName("jvmSourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
}
tasks.getByName("wasmJsSourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
}
tasks.getByName("sourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
}
tasks.getByName("iosArm64SourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
}
tasks.getByName("iosX64SourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
}
tasks.getByName("iosSimulatorArm64SourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
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