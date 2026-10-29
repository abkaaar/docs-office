plugins {
    alias(libs.plugins.docs.office.android.feature)
}

android {
    namespace = "com.docsoffice.app.feature.search"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
