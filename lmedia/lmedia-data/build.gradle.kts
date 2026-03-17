@file:OptIn(ExperimentalWasmDsl::class)

import com.lalilu.*
import com.lalilu.gradle.XcodeDetector
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.pulish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.lmedia"
version = "1.0.0"
extra.set("artifactId", "data")

applyMultiplatform {
    main.dependencies {
        api(project(":lmedia:lmedia-core"))
        api(libs.koin.core)
        api(libs.koin.annotations)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.io)
        api(libs.room3.runtime)
    }
    test.dependencies {
        api(libs.kotlin.test)
        api(libs.kotlinx.coroutines.test)
    }
    androidMain.dependencies {
        api(libs.sqlite.bundled)
        api(libs.androidx.core.ktx)
        api(libs.androidx.test.ktx)
    }
    jvmMain.dependencies {
        api(libs.sqlite.bundled)
    }
    iosMain?.dependencies {
        api(libs.sqlite.bundled)
    }
    val webMain by creating {
        dependencies {
            api(libs.sqlite.web)
            api(libs.kotlinx.browser)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    add("kspJvm", libs.room3.compiler)
    add("kspAndroid", libs.room3.compiler)
    add("kspWasmJs", libs.room3.compiler)
    XcodeDetector.whenXcodeInstalled {
        add("kspIosArm64", libs.room3.compiler)
        add("kspIosSimulatorArm64", libs.room3.compiler)
    }
}

afterEvaluate {
    tasks.named("kspKotlinJvm") {
        dependsOn(tasks.named("kspCommonMainKotlinMetadata"))
    }
    tasks.named("kspDebugKotlinAndroid") {
        dependsOn(tasks.named("kspCommonMainKotlinMetadata"))
    }
}