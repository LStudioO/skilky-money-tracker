package com.vstorchevyi.skilky.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("skilky.detekt")
                apply("skilky.spotless")
                apply("skilky.kover")
            }
        }
}
