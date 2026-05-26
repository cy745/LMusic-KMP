package com.lalilu.gradle

import com.lalilu.Constants
import com.lalilu.gradle.helper.library
import com.lalilu.gradle.helper.libs
import com.lalilu.gradle.helper.notation
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.setupKoin() {
    if (!project.plugins.hasPlugin(Constants.KSP_PLUGIN)) {
        throw IllegalStateException("KSP plugin is required to use Koin")
    }

    val koinCompiler = project.libs.library(Constants.KOIN_COMPILER_ALIAS)?.notation()
        ?: throw IllegalStateException("Koin Compiler is not found for alias [${Constants.KOIN_COMPILER_ALIAS}]")

    commonMainKspDependencies {
        ksp(koinCompiler)
    }
}