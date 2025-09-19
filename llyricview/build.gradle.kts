@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.makeSureAllSourcesJarAfterKsp
import com.lalilu.gradle.setupIOSTarget
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

group = "com.lalilu.llyricview"
version = "1.0.0"

kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    setupIOSTarget {}
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.components.resources)
                api(project(":component"))
                api(project(":llyric"))
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
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
                implementation("org.junit.jupiter:junit-jupiter-engine:5.13.4")
                implementation("org.junit.platform:junit-platform-launcher:1.13.4")
            }
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