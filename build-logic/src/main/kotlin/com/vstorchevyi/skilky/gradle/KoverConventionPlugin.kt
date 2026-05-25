package com.vstorchevyi.skilky.gradle

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Coverage with Kover. Reports surface at `<module>/build/reports/kover/`.
 *
 * `koverVerify` runs but does not enforce a minimum: a uniform floor across
 * the repo would fail thin host modules like `:app:androidApp` and
 * `:app:desktopApp`, which currently hold only entry-point boot wiring. The
 * "did the tests actually run" signal lives in the per-module junit report
 * instead. Reintroduce a module-specific floor here once a ratchet baseline
 * is in place.
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
