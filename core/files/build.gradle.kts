plugins {
    alias(libs.plugins.docs.office.android.library)
    alias(libs.plugins.docs.office.hilt)
}

android {
    namespace = "com.docsoffice.app.core.files"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":renderer:text"))
    implementation(project(":renderer:ooxml"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
