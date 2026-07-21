package com.lalilu.gradle

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.lalilu.Constants
import com.lalilu.gradle.helper.libs
import com.lalilu.gradle.helper.version
import org.gradle.api.Action
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.extra
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget


@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.setupMultiplatform(
    setupAndroidTarget: KotlinMultiplatformAndroidLibraryTarget.() -> Unit = {},
    setupJvmTarget: KotlinJvmTarget.() -> Unit = {},
    setupWasmTarget: KotlinWasmJsTargetDsl.() -> Unit = {},
    setupIosTarget: List<KotlinNativeTarget>.() -> Unit = {},
    enableAndroidResources: Boolean = true
) {
    applyDefaultHierarchyTemplate()

    val artifactId = project.extra.runCatching { get("artifactId") }.getOrNull() as? String?
    val targetNamespace = if (artifactId.isNullOrBlank()) "${project.group}" else "${project.group}.$artifactId"
    val targetCompileSdk = project.libs.version("android.targetSdk")?.displayName?.toIntOrNull()

    androidLibrary {
        namespace = targetNamespace
        compileSdk = targetCompileSdk ?: Constants.FALLBACK_TARGET_SDK

        if (enableAndroidResources) {
            androidResources {
                enable = true
            }
        }

        setupAndroidTarget()
    }

    jvm {
        setupJvmTarget()
    }

    wasmJs {
        browser {
            testTask { enabled = false }
        }
        nodejs {
            testTask { enabled = false }
        }
        binaries.executable()
        binaries.library()
        setupWasmTarget()
    }

    if (!project.disableIosTargets) {
        listOf(
            iosArm64(),
            iosSimulatorArm64()
        ).setupIosTarget()
    }
}

/**
 * Configures the [androidLibrary][KotlinMultiplatformAndroidLibraryTarget] extension.
 */
private fun KotlinMultiplatformExtension.androidLibrary(configure: Action<KotlinMultiplatformAndroidLibraryTarget>): Unit =
    (this as ExtensionAware).extensions.configure("androidLibrary", configure)

