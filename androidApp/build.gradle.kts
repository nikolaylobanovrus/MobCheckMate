plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.mobcheckmate.chess"
    // Compile against Android 17 (API 37) as current AndroidX requires; target stays at the Play minimum.
    compileSdk = 37

    defaultConfig {
        // The application ID cannot change after the first publication. Confirm it before release.
        applicationId = "com.mobcheckmate.chess"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // One code base, one build per store: payments, push transport and store links differ.
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"play\"")
        }
        create("rustore") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"rustore\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // Robolectric renders Compose screens on the JVM for screenshot tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric reads FileDescriptor internals, which JDK 17+ hides by default.
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
