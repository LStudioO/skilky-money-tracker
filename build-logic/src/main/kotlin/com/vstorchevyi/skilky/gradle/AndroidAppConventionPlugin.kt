package com.vstorchevyi.skilky.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("skilky.detekt")
                apply("skilky.spotless")
            }
        }
}
