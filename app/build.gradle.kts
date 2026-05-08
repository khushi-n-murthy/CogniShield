// app/build.gradle.kts
// CogniShield — Khushi N's module (AI Core & Edge Pipeline)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.cognishield"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cognishield"
        minSdk = 30           // Android 11 — required by Samsung Health Sensor SDK
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // TFLite model file must not be compressed in the APK
        aaptOptions {
            noCompress("tflite")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Required for TFLite GPU delegate native libraries
    packaging {
        resources {
            pickFirsts += "META-INF/LICENSE.md"
            pickFirsts += "META-INF/LICENSE-notice.md"
        }
        jniLibs {
            pickFirsts += "**/*.so"
        }
    }
}

dependencies {
    // -------------------------------------------------------------------------
    // Kotlin coroutines
    // -------------------------------------------------------------------------
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // -------------------------------------------------------------------------
    // TensorFlow Lite — model inference
    // -------------------------------------------------------------------------
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // GPU delegate (fallback from NPU)
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")

    // NNAPI delegate — routes to Samsung NPU on Galaxy S-series
    implementation("org.tensorflow:tensorflow-lite-api:2.16.1")

    // -------------------------------------------------------------------------
    // Samsung Health Sensor SDK
    // Place the SDK .aar files in app/libs/ (downloaded from Samsung Developers)
    // -------------------------------------------------------------------------
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // Samsung Wearable (for WearableListenerService base class)
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // -------------------------------------------------------------------------
    // MediaPipe — gaze tracking (face landmark detection)
    // -------------------------------------------------------------------------
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // -------------------------------------------------------------------------
    // CameraX — for feeding frames to GazeGatingAnalyser
    // -------------------------------------------------------------------------
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")

    // -------------------------------------------------------------------------
    // AndroidX core
    // -------------------------------------------------------------------------
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
}
