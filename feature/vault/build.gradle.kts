plugins {
    alias(libs.plugins.docs.office.android.feature)
}

android {
    namespace = "com.docsoffice.app.feature.vault"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:crypto"))
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.activity.compose)
}
