plugins {
    alias(libs.plugins.docs.office.android.feature)
}

android {
    namespace = "com.docsoffice.app.feature.files"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(project(":core:datastore"))
    implementation(project(":core:crypto"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
