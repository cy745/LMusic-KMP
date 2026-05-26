package com.lalilu.gradle

import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget


/**
 * copy from https://github.com/eygraber/gradle-conventions/blob/master/conventions-plugin/src/main/kotlin/ksp.kt
 */
interface KspDependencies {
    fun ksp(dependencyNotation: Any)
}

fun KotlinTarget.kspDependencies(block: KspDependencies.() -> Unit) {
    val configurationName =
        "ksp${targetName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
    project.dependencies {
        object : KspDependencies {
            override fun ksp(dependencyNotation: Any) {
                add(configurationName, dependencyNotation)
            }
        }.block()
    }
}

fun KotlinMultiplatformExtension.kspDependenciesForAllTargets(block: KspDependencies.() -> Unit) {
    targets.configureEach {
        if (targetName != "metadata") {
            kspDependencies(block)
        }
    }
}

fun KotlinMultiplatformExtension.commonMainKspDependencies(
    block: KspDependencies.() -> Unit,
) {
    project.dependencies {
        object : KspDependencies {
            override fun ksp(dependencyNotation: Any) {
                add("kspCommonMainMetadata", dependencyNotation)
            }
        }.block()
    }

    sourceSets.named("commonMain").configure {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }

    project.tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}