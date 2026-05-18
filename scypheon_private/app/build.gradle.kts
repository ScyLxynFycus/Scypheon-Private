plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

kapt {
    correctErrorTypes = true
}

android {
    namespace = "com.scypheon.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scypheon.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.scypheon.app.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            pickFirsts += listOf(
                "lib/arm64-v8a/libggml-base.so",
                "lib/arm64-v8a/libggml.so",
                "lib/arm64-v8a/libggml-cpu.so",
                "lib/arm64-v8a/libllama.so",
                "lib/arm64-v8a/libggml-core.so",
                "lib/arm64-v8a/libggml-opencl.so",
                "lib/arm64-v8a/libggml-vulkan.so"
            )
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Splash Screen API for startup gating
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Dagger Hilt for Dependency Injection
    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-android-compiler:2.55")
    // hiltViewModel() support inside Composables
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager
    val work_version = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$work_version")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Timber for Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation(project(":scypheon_sdk"))
    implementation(project(":llama"))

    // Room Database (Required for DB pre-warming in Application class)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Enterprise Testing Framework
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.55")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.55")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Image Loading (Coil)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ML Kit Vision
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // Coroutines Play Services (for await() on Tasks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // JSON Parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Baseline Profiles for Startup Optimization
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Memory Leak Detection (Debug Only)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
}
