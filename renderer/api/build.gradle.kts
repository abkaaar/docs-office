plugins {
    alias(libs.plugins.docs.office.jvm.library)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
}
