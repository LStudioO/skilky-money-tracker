import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.skilky.kover)
}

// The desktop host module is `main()` plus Compose Desktop packaging config.
// Behavior lives in :app:shared.
kover {
    reports {
        filters {
            excludes {
                classes("com.vstorchevyi.skilky.MainKt")
            }
        }
    }
}

dependencies {
    implementation(projects.app.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
}

compose.desktop {
    application {
        mainClass = "com.vstorchevyi.skilky.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.vstorchevyi.skilky"
            packageVersion = "1.0.0"
        }
    }
}
