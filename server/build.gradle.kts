plugins {
    alias(libs.plugins.skilky.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

// Module-specific Kover exclusions. The base Kover setup (plugin, generic
// codegen exclusions) is applied by skilky.kover via skilky.kotlin-jvm.
kover {
    reports {
        filters {
            excludes {
                classes(
                    // Boot wiring. module() is the DI list, exercised
                    // transitively by route tests; instrumenting it adds
                    // noise without surfacing real coverage gaps.
                    "com.vstorchevyi.skilky.ApplicationKt",
                    // Exposed Table singletons are declarative column
                    // descriptions; nothing branches in them.
                    "com.vstorchevyi.skilky.db.tables.*",
                    // Eval harness runs against a real Ollama outside the
                    // unit-test path. Coverage here would always be 0 in
                    // CI; signal lives in the eval task's own pass/fail.
                    "com.vstorchevyi.skilky.eval.*",
                )
            }
        }
    }
}

group = "com.vstorchevyi.skilky"
version = "1.0.0"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// Eval harness. Runs the model against [EvalFixtures] and fails on F1
// regression. Lives outside :server:test because it hits a real Ollama;
// invoke explicitly when iterating on prompts or models.
//
// Usage:
//   AI_BASE_URL=http://localhost:11434 AI_MODEL=gemma4:e4b ./gradlew :server:evalTest
tasks.register<JavaExec>("evalTest") {
    description = "Grade [EvalFixtures] against a real Ollama and exit 1 on regression."
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.vstorchevyi.skilky.eval.EvalRunnerKt")
    standardInput = System.`in`
}

dependencies {
    implementation(projects.core)

    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverCallId)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.serverAuthJwt)
    implementation(libs.ktor.serverRateLimit)
    implementation(libs.bcrypt)

    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)

    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.kotlinx.serializationJson)

    implementation(libs.logback)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.kotest.assertionsCore)
    testImplementation(libs.ktor.clientMock)
    testImplementation(libs.koin.test)
}
