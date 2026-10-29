plugins {
    alias(libs.plugins.docs.office.android.library)
}

android {
    namespace = "com.docsoffice.app.renderer.pdf"
}

dependencies {
    implementation(project(":renderer:api"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
