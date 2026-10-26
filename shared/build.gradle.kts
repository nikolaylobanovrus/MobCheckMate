import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // JVM target serves both the Android app and the Ktor server.
    // iOS targets are added when the iOS client starts (kchesslib already ships them).
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kchesslib)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Manual engine strength matches: ./gradlew :shared:jvmTest -PengineMatch=4 --tests '*LevelMatchTest*'
tasks.withType<Test>().configureEach {
    providers.gradleProperty("engineMatch").orNull?.let { systemProperty("engineMatch", it) }
    providers.gradleProperty("enginePairs").orNull?.let { systemProperty("enginePairs", it) }
    // Calibration against a local Stockfish binary (never bundled); see StockfishCalibrationTest.
    for (name in listOf("stockfishPath", "stockfishElo", "stockfishMoveTime", "engineLevels")) {
        providers.gradleProperty(name).orNull?.let { systemProperty(name, it) }
    }
    testLogging.showStandardStreams =
        providers.gradleProperty("engineMatch").isPresent || providers.gradleProperty("stockfishPath").isPresent
}
