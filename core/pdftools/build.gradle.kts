plugins {
    alias(libs.plugins.docs.office.android.library)
    alias(libs.plugins.docs.office.hilt)
}

android {
    namespace = "com.docsoffice.app.core.pdftools"

    testOptions {
        // PdfBox merger calls android.util.Log; JVM unit tests need defaults.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
}
