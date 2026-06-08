plugins {
    alias(libs.plugins.skilky.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kover {
    reports {
        filters {
            excludes {
                packages(
                    // Compose Multiplatform resources codegen. Pure generated
                    // accessors; not something to test.
                    "skilky.composeapp.generated.resources",
                    // Koin module declarations and the platform startup glue.
                    // Config-style calls into the Koin DSL with no branching,
                    // exercised transitively by every binding lookup. Same
                    // reason :server excludes ApplicationKt.
                    "com.vstorchevyi.skilky.di",
                )
            }
        }
    }
}

kotlin {
    android {
        namespace = "com.vstorchevyi.skilky.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.vstorchevyi.skilky")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.core)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.ktorMp.core)
            implementation(libs.ktorMp.contentNegotiation)
            implementation(libs.ktorMp.json)
            implementation(libs.ktorMp.auth)
            // `api` so the platform shells (androidApp, desktopApp) see the
            // Koin Module type when they construct their platform modules.
            api(libs.koin.core)
            implementation(libs.koin.composeVm)
            implementation(libs.androidx.datastore.preferencesCore)
            implementation(libs.androidx.navigation.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.compose.uiTest)
            implementation(libs.kotlinx.coroutinesTest)
        }
        // One Ktor engine per platform. The HTTP client is created in
        // commonMain from an expect/actual engine; see HttpClientFactory.
        androidMain.dependencies {
            implementation(libs.ktorMp.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktorMp.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktorMp.cio)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.clientMock)
        }
    }
}

compose.resources {
    packageOfResClass = "skilky.composeapp.generated.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
