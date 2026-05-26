package com.lalilu.gradle.helper

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.VersionConstraint
import kotlin.jvm.optionals.getOrNull

val Project.libs: VersionCatalog
    get() = libs()

fun Project.libs(alias: String = "libs"): VersionCatalog =
    extensions.getByType(VersionCatalogsExtension::class.java)
        .named(alias)

fun VersionCatalog.library(alias: String): MinimalExternalModuleDependency? =
    findLibrary(alias).getOrNull()?.get()

fun VersionCatalog.version(alias: String): VersionConstraint? =
    findVersion(alias).getOrNull()

fun MinimalExternalModuleDependency.notation(): String =
    "${module}:${version}"