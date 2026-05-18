plugins {
    // Loaded at the root so subprojects share classloaders.
    // Required for plugins that register shared build services (e.g. Spotless).
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.spotless) apply false
    // Kover at the root acts as an aggregator. Running `./gradlew koverXmlReport
    // koverHtmlReport` here merges binary reports from the modules listed in
    // `dependencies { kover(...) }` into a single project-wide report at
    // `build/reports/kover/`.
    alias(libs.plugins.kover)
}

dependencies {
    kover(projects.core)
    kover(projects.server)
    kover(project(":app:shared"))
}