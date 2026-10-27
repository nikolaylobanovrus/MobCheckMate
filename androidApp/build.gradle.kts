plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ru.fourvic.chess"
    // Compile against Android 17 (API 37) as current AndroidX requires; target stays at the Play minimum.
    compileSdk = 37

    defaultConfig {
        // Reverse of the owner's domain 4vic.ru; "4vic" becomes "fourvic" because every segment must start
        // with a letter. The application ID cannot change after the first publication.
        applicationId = "ru.fourvic.chess"
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
        debug {
            // The emulator reaches the host machine at 10.0.2.2; run the server locally with ./gradlew :server:run.
            buildConfigField("String", "SERVER_URL", "\"ws://10.0.2.2:8080/ws\"")
        }
        release {
            buildConfigField("String", "SERVER_URL", "\"wss://chess.4vic.ru/ws\"")
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
            // Address of a running server for OnlineClientIntegrationTest, e.g. -PonlineServer=ws://localhost:8080/ws.
            providers.gradleProperty("onlineServer").orNull?.let { url -> it.systemProperty("onlineServer", url) }
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
    implementation(libs.ktor.client.okhttp)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
