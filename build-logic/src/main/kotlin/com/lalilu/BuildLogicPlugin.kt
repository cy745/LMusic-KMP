package com.lalilu

import org.gradle.api.Plugin
import org.gradle.api.Project


class BuildLogicPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.logger.warn("[build-logic] plugin applied on ${target.name}")
    }
}

