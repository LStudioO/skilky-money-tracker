plugins {
    alias(libs.plugins.skilky.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

// The Compose Multiplatform resources plugin generates a `Res` accessor
// class under `packageOfResClass`. It's pure codegen, not something we
// write tests against; counting it just pulls the module's coverage down.
kover {
    reports {
        filters {
            excludes {
                packages("skilky.composeapp.generated.resources")
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
            implementation(libs.koin.core)
            implementation(libs.androidx.datastore.preferencesCore)
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
        }
    }
}

compose.resources {
    packageOfResClass = "skilky.composeapp.generated.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
