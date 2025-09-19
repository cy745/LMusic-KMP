package com.lalilu.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget


/**
 * 设置 iOS 目标平台
 */
fun KotlinMultiplatformExtension.setupIOSTarget(
    configure: KotlinNativeTarget.() -> Unit = {}
) {
    if (XcodeDetector.isXcodeInstalled()) {
        println("Xcode found, configuring iOS targets")
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach {
            it.configure()
        }
    } else {
        println("Xcode not found, skipping iOS target configuration")
    }
}