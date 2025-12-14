package com.lalilu.gradle

import com.lalilu.Constants
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinCommonCompilation
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

            targets.forEach { target ->
                target.compilations.forEach { compilation ->
                    val configurationName = getKotlinConfigurationName(compilation) ?: return@forEach

                    project.dependencies.apply {
                        add(configurationName, sweetSpiProcessor)
                        add(compilation.defaultSourceSet.implementationConfigurationName, sweetSpiRuntime)
                    }
                }
            }
        }
    }
}

// tries to mimic KSP logic...
private fun getKotlinConfigurationName(compilation: KotlinCompilation<*>): String? {
    val isMain = compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME
    // Note: on single-platform, the target name is conveniently set to "".
    val name = when {
        // skip, this will be dropped and unused now
        isMain && compilation is KotlinCommonCompilation -> return null
        isMain -> compilation.target.name
        compilation is KotlinCommonCompilation -> {
            compilation.defaultSourceSet.name + compilation.target.name.replaceFirstChar(Char::uppercase)
        }

        else -> compilation.defaultSourceSet.name
    }
    return "ksp" + name.replaceFirstChar(Char::uppercase)
}