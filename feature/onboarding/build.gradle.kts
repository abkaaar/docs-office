plugins {
    alias(libs.plugins.docs.office.android.feature)
}

android {
    namespace = "com.docsoffice.app.feature.onboarding"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:files"))
    implementation(libs.androidx.activity.compose)
}
