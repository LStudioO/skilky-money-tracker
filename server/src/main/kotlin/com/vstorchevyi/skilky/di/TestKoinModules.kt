package com.vstorchevyi.skilky.di

import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import org.koin.core.module.Module

/**
 * Test-side hook: a test installs a Koin module list under this key in
 * `application { attributes.put(...) }` before [com.vstorchevyi.skilky.module]
 * runs, and the production modules are layered with those overrides on
 * top. Replaces the per-service `AttributeKey` patterns from earlier; one
 * hook covers every service the test wants to swap.
 *
 * Production code never reads or writes this key.
 */
val TestKoinModulesKey: AttributeKey<List<Module>> = AttributeKey("skilky.testKoinModules")

internal fun Application.testKoinModules(): List<Module> =
    if (attributes.contains(TestKoinModulesKey)) attributes[TestKoinModulesKey] else emptyList()
