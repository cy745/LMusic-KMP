package com.lalilu.gradle

import com.lalilu.Constants
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.jvm.optionals.getOrNull

fun Project.applySweetSpi() {
    if (!plugins.hasPlugin(Constants.KSP_PLUGIN)) {
        throw RuntimeException("Please apply ksp plugin first")
    }

    val versionCategory = extensions.getByType(VersionCatalogsExtension::class.java)
    val libs = versionCategory.named("libs")
    val sweetSpiProcessor = libs.findLibrary(Constants.SWEET_SPI_PROCESSOR_ALIAS)
        .getOrNull()?.get()
        ?.let { "${it.module}:${it.version}" }
        ?: throw IllegalStateException("sweetSpiProcessor is not found for alias [${Constants.SWEET_SPI_PROCESSOR_ALIAS}]")

    val sweetSpiRuntime = libs.findLibrary(Constants.SWEET_SPI_RUNTIME_ALIAS)
        .getOrNull()?.get()
        ?.let { "${it.module}:${it.version}" }
        ?: throw IllegalStateException("sweetSpiRuntime is not found for alias [${Constants.SWEET_SPI_RUNTIME_ALIAS}]")

    plugins.withId(Constants.KOTLIN_MULTIPLATFORM_PLUGIN) {
        extensions.configure<KotlinMultiplatformExtension> {
            commonMainKspDependencies {
                ksp(sweetSpiProcessor)
            }
            kspDependenciesForAllTargets {
                ksp(sweetSpiProcessor)
            }

            targets.forEach { target ->
                target.compilations.forEach { compilation ->
                    val configurationName = compilation.defaultSourceSet.implementationConfigurationName
                    project.dependencies.add(configurationName, sweetSpiRuntime)
                }
            }
        }
    }
}