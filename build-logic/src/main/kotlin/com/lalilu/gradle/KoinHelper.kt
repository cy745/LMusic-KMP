package com.lalilu.gradle

import com.lalilu.Constants
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import kotlin.jvm.optionals.getOrNull

fun Project.applyKoin() {
    if (!plugins.hasPlugin(Constants.KSP_PLUGIN)) {
        throw IllegalStateException("KSP plugin is required to use Koin")
    }

    val versionCategory = extensions.getByType(VersionCatalogsExtension::class.java)
    val libs = versionCategory.named("libs")

    val koinCompiler = libs.findLibrary(Constants.KOIN_COMPILER_ALIAS).getOrNull()?.get()
        ?.let { "${it.module}:${it.version}" }
        ?: throw IllegalStateException("Koin Compiler is not found for alias [${Constants.KOIN_COMPILER_ALIAS}]")

    dependencies {
        add("kspCommonMainMetadata", koinCompiler)
    }
}