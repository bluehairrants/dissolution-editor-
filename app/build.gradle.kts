plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.dissolution.editor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dissolution.editor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "BOSSAI_BASE_URL", providers.gradleProperty("bossAiBaseUrl").orElse("").get().asBuildConfigString())
        buildConfigField("String", "BOSSAI_MODEL", providers.gradleProperty("bossAiModel").orElse("").get().asBuildConfigString())
        buildConfigField("String", "BOSSAI_API_KEY", providers.gradleProperty("bossAiApiKey").orElse("").get().asBuildConfigString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.arthenica:ffmpeg-kit-full:6.0-2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}