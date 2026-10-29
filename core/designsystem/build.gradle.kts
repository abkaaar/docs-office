plugins {
    alias(libs.plugins.docs.office.android.library)
    alias(libs.plugins.docs.office.android.compose)
}

android {
    namespace = "com.docsoffice.app.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.compose.material.icons)
}
