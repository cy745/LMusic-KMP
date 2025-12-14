package com.lalilu.gradle

import com.lalilu.Constants
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra

fun Project.applyPublish() {
    if (!plugins.hasPlugin(Constants.VANNIKTECH_PUBLISH_PLUGIN)) {
        logger.warn("[${Constants.VANNIKTECH_PUBLISH_PLUGIN}] is not applied, skip publish.")
        return
    }

    val hasDokka = plugins.hasPlugin(Constants.DOKKA_DOCS_PLUGIN)

    val artifactId = extra.get("artifactId")?.toString()
    if (artifactId.isNullOrBlank()) {
        throw IllegalArgumentException("artifactId is null or blank")
    }

    plugins.withId(Constants.VANNIKTECH_PUBLISH_PLUGIN) {
        extensions.configure<MavenPublishBaseExtension> {
            coordinates(
                groupId = group.toString(),
                version = version.toString(),
                artifactId = artifactId,
            )

            configure(
                KotlinMultiplatform(
                    javadocJar = if (hasDokka) JavadocJar.Dokka("dokkaGenerate") else JavadocJar.None(),
                    sourcesJar = true,
                )
            )

            pom {
                name.set("LMedia")
                description.set("LMedia with $artifactId")
                inceptionYear.set("2025")
            }

            publishToMavenCentral(true)
            //    signAllPublications()
        }
    }
}