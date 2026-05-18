pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.2"
        id("com.android.library") version "8.7.2"
        id("org.jetbrains.kotlin.android") version "2.1.10"
        id("org.jetbrains.kotlin.kapt") version "2.1.10"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
        id("com.google.dagger.hilt.android") version "2.55"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.google.com") }
    }
}

rootProject.name = "ScypheonPrivate"
include(":app")
include(":scypheon_sdk")
include(":llama")

project(":app").projectDir = File(settingsDir, "scypheon_private/app")
project(":scypheon_sdk").projectDir = File(settingsDir, "scypheon_sdk")
project(":llama").projectDir = File(settingsDir, "llama")
