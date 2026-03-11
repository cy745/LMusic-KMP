package com.lalilu

import com.lalilu.gradle.*
import org.gradle.api.Project
import org.gradle.kotlin.dsl.NamedDomainObjectContainerScope
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet


fun Project.applyMultiplatform(
    dependenciesBlock: NamedDomainObjectContainerScope<KotlinSourceSet>.() -> Unit
) {
    applyMultiplatform(
        configureBlock = { },
        dependenciesBlock = dependenciesBlock
    )
}

fun Project.applyMultiplatform(
    configureBlock: KotlinMultiplatformExtension.() -> Unit,
    dependenciesBlock: NamedDomainObjectContainerScope<KotlinSourceSet>.() -> Unit
) {
    if (!plugins.hasPlugin(Constants.KOTLIN_MULTIPLATFORM_PLUGIN)) {
        throw IllegalStateException("Please add kotlin multiplatform plugin [${Constants.KOTLIN_MULTIPLATFORM_PLUGIN}] to your project")
    }

    plugins.withId(Constants.KOTLIN_MULTIPLATFORM_PLUGIN) {
        extensions.configure<KotlinMultiplatformExtension> {
            val isAndroidApp = plugins.hasPlugin(Constants.ANDROID_APPLICATION_PLUGIN)
            val isAndroidLibrary = plugins.hasPlugin(Constants.ANDROID_LIBRARY_PLUGIN)
            if (isAndroidApp || isAndroidLibrary) {
                androidTarget {
                    if (isAndroidLibrary) {
                        publishLibraryVariants("release")
                    }
                }
            }

            jvm()

            @OptIn(ExperimentalWasmDsl::class)
            wasmJs {
                browser {
                    testTask {
                        enabled = false
                    }
                }
                nodejs {
                    testTask {
                        enabled = false
                    }
                }
                binaries.executable()
                binaries.library()
            }

            XcodeDetector.whenXcodeInstalled {
                listOf(
                    iosArm64(),
                    iosSimulatorArm64()
                )
            }

            configureBlock()

            sourceSets {
                dependenciesBlock()
            }
        }
    }

    applySweetSpi()
    applyAndroidLibrary()
    applyKoin()
    applyPublish()
}


val NamedDomainObjectContainerScope<KotlinSourceSet>.main
    get() = getByName("commonMain")

val NamedDomainObjectContainerScope<KotlinSourceSet>.test
    get() = getByName("commonTest")

val NamedDomainObjectContainerScope<KotlinSourceSet>.androidMain
    get() = getByName("androidMain")

val NamedDomainObjectContainerScope<KotlinSourceSet>.iosMain: KotlinSourceSet?
    get() = runCatching { getByName("iosMain") }.getOrNull()

val NamedDomainObjectContainerScope<KotlinSourceSet>.jvmMain
    get() = getByName("jvmMain")

val NamedDomainObjectContainerScope<KotlinSourceSet>.wasmJsMain
    get() = getByName("wasmJsMain")