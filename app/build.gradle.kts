plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aaronhrndz.ultraremapper"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.aaronhrndz.ultraremapper"
        minSdk = 30
        targetSdk = 37
        versionCode = 89
        versionName = "1.6.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/signing/release.jks")
            storePassword = "ultraremapper"
            keyAlias = "ultraremapper"
            keyPassword = "ultraremapper"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**", "com.aaronhrndz.**")
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.remote.interactions)
    implementation(libs.play.services.wearable)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
