plugins {
    alias(libs.plugins.docs.office.android.library)
    alias(libs.plugins.docs.office.hilt)
}

android {
    namespace = "com.docsoffice.app.core.datastore"
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
