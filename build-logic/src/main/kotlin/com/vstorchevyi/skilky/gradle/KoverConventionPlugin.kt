package com.vstorchevyi.skilky.gradle

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Coverage with Kover. Reports surface at `<module>/build/reports/kover/`.
 *
 * Each module enforces a 1% line-coverage floor via `koverVerify`. This is
 * a low bar on purpose: it catches "tests don't run at all" without
 * encouraging tests that hit numbers without testing intent. Bump the
 * floor per-module here as confidence grows.
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
                    verify {
                        rule {
                            minBound(1)
                        }
                    }
                }
            }
        }
}
