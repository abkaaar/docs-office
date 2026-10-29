plugins {
    alias(libs.plugins.docs.office.jvm.library)
}

dependencies {
    implementation(project(":renderer:api"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
