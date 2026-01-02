package com.lalilu.gradle

import com.android.build.gradle.LibraryExtension
import com.lalilu.Constants
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.internal.extensions.core.extra
import org.gradle.kotlin.dsl.configure
import kotlin.jvm.optionals.getOrNull

fun Project.applyAndroidLibrary() {
    if (!plugins.hasPlugin(Constants.ANDROID_LIBRARY_PLUGIN)) {
        throw IllegalStateException("Android Library Plugin is not applied")
    }

    val versionCategory = extensions.getByType(VersionCatalogsExtension::class.java)
    val libs = versionCategory.named("libs")

    val targetSdk = libs.findVersion("android.targetSdk")
        .getOrNull()
        ?.displayName

    plugins.withId(Constants.ANDROID_LIBRARY_PLUGIN) {
        extensions.configure<LibraryExtension> {
            val artifactId = "${runCatching { extra.get("artifactId") }.getOrNull() ?: ""}"
            namespace = if (artifactId.isNotBlank()) "$group.$artifactId" else "$group"
            compileSdk = targetSdk?.toIntOrNull() ?: Constants.FALLBACK_TARGET_SDK
        }
    }
}