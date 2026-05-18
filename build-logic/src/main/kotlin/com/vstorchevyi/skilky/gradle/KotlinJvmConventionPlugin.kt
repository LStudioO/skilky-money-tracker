package com.vstorchevyi.skilky.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
                apply("skilky.detekt")
                apply("skilky.spotless")
                apply("skilky.kover")
            }
            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(21)
            }
            // Surface test outcomes and counts on every Test task. Gradle's
            // default output is "BUILD SUCCESSFUL", which hides whether 1
            // test or 100 ran. In CI logs the summary line is the only
            // signal of what actually happened.
            tasks.withType<Test>().configureEach {
                testLogging {
                    events(
                        TestLogEvent.PASSED,
                        TestLogEvent.FAILED,
                        TestLogEvent.SKIPPED,
                    )
                    exceptionFormat = TestExceptionFormat.FULL
                    showStackTraces = true
                }
                addTestListener(TestSummaryListener())
            }
        }
}

/**
 * Prints a one-line summary when the whole test run finishes.
 * The root suite is the one with no parent.
 */
private class TestSummaryListener : TestListener {
    override fun beforeSuite(suite: TestDescriptor) = Unit

    override fun beforeTest(testDescriptor: TestDescriptor) = Unit

    override fun afterTest(
        testDescriptor: TestDescriptor,
        result: TestResult,
    ) = Unit

    override fun afterSuite(
        suite: TestDescriptor,
        result: TestResult,
    ) {
        if (suite.parent != null) return
        val outcome =
            when (result.resultType) {
                TestResult.ResultType.SUCCESS -> "PASS"
                TestResult.ResultType.FAILURE -> "FAIL"
                TestResult.ResultType.SKIPPED -> "SKIP"
            }
        println(
            "Tests $outcome: ${result.testCount} total, " +
                "${result.successfulTestCount} passed, " +
                "${result.failedTestCount} failed, " +
                "${result.skippedTestCount} skipped",
        )
    }
}
