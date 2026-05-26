package com.lalilu.gradle

import com.lalilu.Constants
import com.lalilu.gradle.helper.library
import com.lalilu.gradle.helper.libs
import com.lalilu.gradle.helper.notation
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.setupSweetSpi() {
    if (!project.plugins.hasPlugin(Constants.KSP_PLUGIN)) {
        throw RuntimeException("Please apply ksp plugin first")
    }

    val sweetSpiProcessor = project.libs.library(Constants.SWEET_SPI_PROCESSOR_ALIAS)?.notation()
        ?: throw IllegalStateException("sweetSpiProcessor is not found for alias [${Constants.SWEET_SPI_PROCESSOR_ALIAS}]")

    val sweetSpiRuntime = project.libs.library(Constants.SWEET_SPI_RUNTIME_ALIAS)?.notation()
        ?: throw IllegalStateException("sweetSpiRuntime is not found for alias [${Constants.SWEET_SPI_RUNTIME_ALIAS}]")

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