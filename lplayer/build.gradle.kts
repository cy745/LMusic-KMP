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
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
    alias(libs.plugins.swiftklib)
    alias(libs.plugins.osdetector)
}

group = "com.lalilu.lplayer"
version = "1.0.0"

kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.compilations {
            val main by getting {
                cinterops {
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
                api(project(":lmedia"))
                api(libs.koin.core)
                api(libs.koin.annotations)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.io)
                api(libs.filekit.core)
                api(libs.bundles.settings)
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
            implementation(compose.preview)
            implementation(libs.media3.session)
            implementation(libs.media3.exoplayer)
        }
        jvmMain.dependencies {
            implementation(libs.vlcj)
        }
    }
}

//swiftklib {
//    create("MusicKitWrapper") {
//        path = file("native/MusicKitWrapper")
//        packageName("com.lalilu.lmedia")
//    }
//}

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
        name = "LPlayer"
        description = "LPlayer"
        inceptionYear = "2025"
    }

    publishToMavenCentral(true)
//    signAllPublications()
}