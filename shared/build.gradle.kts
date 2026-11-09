plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "dev.wonderdesk.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    api("androidx.core:core-ktx:1.13.1")
    api("androidx.activity:activity-compose:1.9.2")
    api("androidx.compose.material3:material3:1.2.1")
    api("androidx.compose.foundation:foundation:1.6.8")
    api("androidx.documentfile:documentfile:1.0.1")
    api("androidx.media3:media3-exoplayer:1.4.1")
    api("androidx.media3:media3-session:1.4.1")
    api("androidx.media3:media3-ui:1.4.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
