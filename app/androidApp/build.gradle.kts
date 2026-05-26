plugins {
    alias(libs.plugins.skilky.androidApp)
    alias(libs.plugins.composeCompiler)
}

// The Android host module is thin: an Application that starts Koin, an
// Activity that mounts the shared `App()` composable, and a Compose @Preview
// stub. None of that branches enough to test usefully; behavior lives in
// :app:shared and gets covered there.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.vstorchevyi.skilky.SkilkyApplication",
                    "com.vstorchevyi.skilky.MainActivity",
                    "com.vstorchevyi.skilky.MainActivityKt",
                )
            }
        }
    }
}

android {
    namespace = "com.vstorchevyi.skilky"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.vstorchevyi.skilky"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.app.shared)
    implementation(libs.androidx.activity.compose)
    // The @Preview annotation in MainActivity is referenced from every build
    // variant. `ui-tooling` (debug only) brings it in transitively for debug,
    // but Kover instruments the release variant, so the annotation artifact
    // has to be on the release classpath too.
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    testImplementation(libs.kotlin.testJunit)
}
