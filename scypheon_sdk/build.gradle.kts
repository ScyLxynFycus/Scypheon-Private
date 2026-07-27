import java.security.MessageDigest
import java.io.File

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("app.cash.sqldelight") version "2.0.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
    jacoco
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("org.cyclonedx.bom") version "1.10.0"
}

android {
    namespace = "com.scypheon.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        ndk {
            abiFilters.add("arm64-v8a")
        }
        buildConfigField("String", "REMOTE_CONFIG_BACKEND", "\"github\"")
        buildConfigField("int", "VERSION_CODE", "1")
        buildConfigField("String", "CORPUS_VERIFICATION_PUBLIC_KEY", "\"04a3b2c1d0e9f804a3b2c1d0e9f804a3b2c1d0e9f804a3b2c1d0e9f8\"")
        buildConfigField("String", "CORPUS_VERIFICATION_KEY_ID", "\"scypheon-corpus-2026-q2\"")
        buildConfigField("String", "MODEL_MANIFEST_PUBLIC_KEY", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }
    
    lint {
        abortOnError = false
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            pickFirsts.add("lib/**/libggml*.so")
            pickFirsts.add("lib/**/libllama*.so")
            pickFirsts.add("**/libc++_shared.so")
        }
    }
}

sqldelight {
    databases {
        create("MedicalDatabase") {
            packageName.set("com.scypheon.sdk.db")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
        }
    }
}

kapt {
    correctErrorTypes = true
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // LiteRT-LM (Modern Google AI Edge Engine for Gemma 4)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")

    // Modern Networking for Model Provisioning (Hugging Face Gated)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Dependency Injection (Hilt)
    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-android-compiler:2.55")

    // Memory & Vector Ops
    // Llama.cpp Native Fallback Engine (with TurboQuant support)
    implementation(project(":llama"))

    // Google Native AI Stack (TFLite / MediaPipe / ML Kit) for Hackathon
    // Gemma LLM Inference via MediaPipe Tasks GenAI
    implementation("com.google.mediapipe:tasks-genai:0.10.29")

    // Offline Embeddings & Semantic Search via MediaPipe Text
    implementation("com.google.mediapipe:tasks-text:0.10.14")

    // Offline Vision (Fallback/Auxiliary)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Offline Audio Classification (LiteRT / MediaPipe) for Deaf Environment Guardian
    implementation("com.google.mediapipe:tasks-audio:0.10.14")

    // ML Kit for Offline OCR (Visual Memory / Puppet Master)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // Coroutines support for asynchronous Google API calls
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Lifecycle scopes for UI Coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // CameraX for Live Vision / Sign Language Accessibilty
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Enterprise Testing Framework
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Room DB for Local Storage
    val roomVersion = "2.7.0-alpha12"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // SQLDelight
    implementation("app.cash.sqldelight:android-driver:2.0.2")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")

    // PDF Generation & Parsing
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // HTML Parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Security & Biometrics
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("net.zetetic:sqlcipher-android:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

    // PDF Extraction & HTML Parsing
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.jsoup:jsoup:1.17.2")
}

// 🛡️ SOLARIS PROVENANCE: SHA-256 Compile-Time Generation Task
tasks.register("generateModelHashes") {
    val modelDir = file("src/main/assets/models")
    val outputFile = file("src/main/java/com/scypheon/sdk/core/utils/ModelHashes.kt")
    
    inputs.dir(modelDir)
    outputs.file(outputFile)

    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashes = mutableMapOf<String, String>()
        
        if (modelDir.exists()) {
            modelDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val hash = file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            digest.update(buffer, 0, bytesRead)
                        }
                        digest.digest().joinToString("") { byte: Byte -> "%02x".format(byte) }
                    }
                    val constName = file.name.uppercase().replace(".", "_").replace("-", "_")
                    hashes[constName] = hash
                }
            }
        }

        outputFile.parentFile.mkdirs()
        outputFile.writeText("""
            package com.scypheon.sdk.core.utils

            /**
             * GENERATED BY GRADLE SolarisProvenanceTask. DO NOT EDIT MANUALLY.
             * Hardcoded SHA-256 Provenance for Deterministic Asset Hardening.
             */
            object ModelHashes {
                ${hashes.entries.joinToString("\n                ") { "const val ${it.key} = \"${it.value}\"" }}
                
                // Fallback / Placeholder for Build Stability if assets missing during CI
                const val GEMMA_2B_Q6_PLACEHOLDER = "PLACEHOLDER_HASH"
            }
        """.trimIndent())
    }
}

// Ensure hashes are generated before compilation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("generateModelHashes")
}

// ═══════════════════════════════════════════════════════════════════
// ── JaCoCo Code Coverage ─────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*",
        "**/Manifest*.*", "**/*Test*.*", "**/di/*",
        "**/*_Factory.*", "**/*_MembersInjector.*",
        "**/databinding/**", "**/binding/**"
    )

    val debugTree = layout.buildDirectory.dir("intermediates/javac/debug/classes").get().asFile
    val kotlinSrc = layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile

    sourceDirectories.setFrom(files(debugTree, kotlinSrc))
    classDirectories.setFrom(
        files(
            tree(classDirectories) { matching ->
                fileFilter.forEach { matching.exclude(it) }
            }
        )
    )
    executionData.setFrom(
        layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")
    )

    additionalSourceDirs.setFrom(files("src/main/java", "src/main/kotlin"))
}

tasks.register("checkCoverage") {
    dependsOn("jacocoTestReport")
    doLast {
        val reportDir = layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html").get().asFile
        if (!reportDir.exists()) {
            throw GradleException("JaCoCo report not found at ${reportDir.absolutePath}")
        }
        println("✅ JaCoCo coverage report generated at: ${reportDir.absolutePath}")
    }
}
