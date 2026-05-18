plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("detekt") {
            id = "skilky.detekt"
            implementationClass = "com.vstorchevyi.skilky.gradle.DetektConventionPlugin"
        }
        register("spotless") {
            id = "skilky.spotless"
            implementationClass = "com.vstorchevyi.skilky.gradle.SpotlessConventionPlugin"
        }
        register("kotlinJvm") {
            id = "skilky.kotlin-jvm"
            implementationClass = "com.vstorchevyi.skilky.gradle.KotlinJvmConventionPlugin"
        }
        register("kotlinMultiplatform") {
            id = "skilky.kotlin-multiplatform"
            implementationClass = "com.vstorchevyi.skilky.gradle.KotlinMultiplatformConventionPlugin"
        }
        register("androidApp") {
            id = "skilky.android-app"
            implementationClass = "com.vstorchevyi.skilky.gradle.AndroidAppConventionPlugin"
        }
        register("kover") {
            id = "skilky.kover"
            implementationClass = "com.vstorchevyi.skilky.gradle.KoverConventionPlugin"
        }
    }
}
