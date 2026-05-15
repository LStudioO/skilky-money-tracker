package com.vstorchevyi.skilky.gradle

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.diffplug.spotless")

            val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion

            extensions.configure<SpotlessExtension> {
                lineEndings = LineEnding.UNIX

                kotlin {
                    target("src/**/*.kt")
                    targetExclude("**/build/**", "**/generated/**")
                    ktlint(ktlintVersion).editorConfigOverride(
                        mapOf(
                            "ktlint_standard_function-naming" to "disabled",
                            "ktlint_standard_filename" to "disabled",
                            "ktlint_standard_chain-method-continuation" to "disabled",
                        ),
                    )
                    trimTrailingWhitespace()
                    endWithNewline()
                }
                kotlinGradle {
                    target("*.gradle.kts")
                    ktlint(ktlintVersion)
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }
        }
}
