plugins {
    alias(libs.plugins.docs.office.android.library)
    alias(libs.plugins.docs.office.hilt)
}

android {
    namespace = "com.docsoffice.app.core.billing"
}

dependencies {
    implementation(libs.billing.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:datastore"))
}
