@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.lhome"
version = "1.0.0"

kotlin {
    jvm()
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = group.toString()
        compileSdk = libs.versions.android.targetSdk.get().toInt()

        compilations.configureEach {
            compilerOptions.configure {
                jvmTarget = JvmTarget.JVM_11
            }
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
                implementation(project(":component"))
                implementation(project(":lmedia"))
                implementation(libs.remixicon.kmp)
                implementation(libs.compose.material3.window.size)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

dependencies {
    kspCommonMainMetadata(libs.koin.compiler)
}