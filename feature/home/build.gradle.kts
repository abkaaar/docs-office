plugins {
    alias(libs.plugins.docs.office.android.feature)
}

android {
    namespace = "com.docsoffice.app.feature.home"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:files"))

    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
