package com.vstorchevyi.skilky.gradle

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Coverage with Kover. Reports surface at `<module>/build/reports/kover/`.
 *
 * No verification threshold is configured here on purpose: coverage in
 * this project is a visibility tool, not a quality gate. Threshold gates
 * encourage tests that hit numbers without testing intent.
 *
 * Module-specific exclusions (boot wiring, Exposed Table singletons, DTOs)
 * belong in the consuming module's build script, not here.
 */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KoverProjectExtension> {
                reports {
                    filters {
                        excludes {
                            // Generated code Kover should never count. Adding
                            // more universally-generated patterns here is fine;
                            // anything project-specific goes in the module's
                            // build.gradle.kts instead.
                            classes(
                                "*\$\$serializer", // kotlinx-serialization codegen
                                "*ComposableSingletons*", // Compose codegen
                            )
                        }
                    }
                }
            }
        }
}
