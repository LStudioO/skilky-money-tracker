package com.vstorchevyi.skilky.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * `runTest` with a `StandardTestDispatcher` installed as `Dispatchers.Main`
 * for the duration of the test body, and reset afterwards. The KMP-friendly
 * stand-in for the Android `MainDispatcherRule` pattern: JUnit `@Rule`s only
 * live on the JVM, so this lives in `commonTest` as an ordinary function.
 *
 * The dispatcher is built from the `TestScope.testScheduler` so the body can
 * call `advanceUntilIdle()` / `runCurrent()` straight on the receiver, the
 * same way it would after a manual `setMain(StandardTestDispatcher(scheduler))`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runTestWithMain(testBody: suspend TestScope.() -> Unit): TestResult =
    runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }
