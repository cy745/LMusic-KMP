@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupMultiplatform
import com.lalilu.gradle.setupSweetSpi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.llyric"
version = "1.0.0"

kotlin {
    setupMultiplatform()
    setupKoin()
    setupSweetSpi()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io)
            api(libs.kotlinx.serialization)
            api(libs.xmlutil.core)
            api(libs.xmlutil.serialization)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
