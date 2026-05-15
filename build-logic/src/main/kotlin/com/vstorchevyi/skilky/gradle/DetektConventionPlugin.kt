package com.vstorchevyi.skilky.gradle

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("dev.detekt")
            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                autoCorrect = false
                parallel = true
                source.setFrom(
                    fileTree("src") {
                        include("**/*.kt")
                        exclude(
                            "**/test/**",
                            "**/*Test/**",
                            "**/androidTest/**",
                            "**/build/**",
                            "**/generated/**",
                        )
                    },
                )
            }
        }
}
