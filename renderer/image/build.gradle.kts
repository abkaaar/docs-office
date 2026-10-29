plugins {
    alias(libs.plugins.docs.office.android.library)
}

android {
    namespace = "com.docsoffice.app.renderer.image"
}

dependencies {
    implementation(project(":renderer:api"))
    implementation(libs.coil.compose)
}
