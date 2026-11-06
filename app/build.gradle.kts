import java.util.Properties

plugins {
    alias(libs.plugins.docs.office.android.application)
    alias(libs.plugins.docs.office.android.compose)
    alias(libs.plugins.docs.office.hilt)
    // Google services Gradle plugin (processes google-services.json)
    id("com.google.gms.google-services")
    alias(libs.plugins.firebase.crashlytics)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.docsoffice.app"

    defaultConfig {
        applicationId = "com.docsoffice.app"
        versionCode = 2
        versionName = "0.1.1"
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

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("staging") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // Firebase BoM 33.x matches Kotlin 2.1; 35.x needs Kotlin 2.2+ metadata.
    // Crashlytics only — Analytics unused and pulled Kotlin 2.4 measurement APIs.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

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
