package com.lalilu.gradle

import org.gradle.api.Project

/**
 * 确保SourcesJar在ksp之后运行
 */
fun Project.makeSureAllSourcesJarAfterKsp() {
    tasks.filter { it.name.endsWith("SourcesJar") }
        .forEach { task -> task.dependsOn("kspCommonMainKotlinMetadata") }
}