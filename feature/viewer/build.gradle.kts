plugins {
    alias(libs.plugins.docs.office.android.feature)
}

android {
    namespace = "com.docsoffice.app.feature.viewer"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(project(":core:datastore"))
    implementation(project(":core:pdftools"))
    implementation(project(":renderer:api"))
    implementation(project(":renderer:pdf"))
    implementation(project(":renderer:text"))
    implementation(project(":renderer:image"))
    implementation(project(":renderer:ooxml"))
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
}
