plugins {
    alias(libs.plugins.docs.office.android.application)
    alias(libs.plugins.docs.office.android.compose)
    alias(libs.plugins.docs.office.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.docsoffice.app"

    defaultConfig {
        applicationId = "com.docsoffice.app"
        versionCode = 1
        versionName = "0.1.0"
        // Production DocsOfficeApp is @HiltAndroidApp; instrumentation uses the real
        // Hilt graph (AndroidJUnitRunner) so MainActivity injection matches release.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        getByName("staging") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:files"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:crypto"))
    implementation(project(":renderer:api"))
    implementation(project(":renderer:pdf"))
    implementation(project(":renderer:text"))
    implementation(project(":renderer:image"))
    implementation(project(":renderer:ooxml"))
    implementation(project(":core:billing"))
    implementation(project(":core:pdftools"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:files"))
    implementation(project(":feature:search"))
    implementation(project(":feature:viewer"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:vault"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.timber)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    kspAndroidTest(libs.hilt.compiler)
}

// Fail CI if the staging AAB exceeds the size budget (25 MiB download target).
tasks.register("checkBundleSize") {
    dependsOn("bundleStaging")
    doLast {
        val bundle = layout.buildDirectory.file("outputs/bundle/staging/app-staging.aab").get().asFile
        check(bundle.exists()) { "Missing staging bundle at ${bundle.absolutePath}" }
        val maxBytes = 40L * 1024L * 1024L // AAB budget before Play ABI splits
        check(bundle.length() <= maxBytes) {
            "Staging bundle ${bundle.length()} bytes exceeds budget $maxBytes"
        }
        println("Staging bundle size OK: ${bundle.length()} bytes")
    }
}
