# Scypheon Private: Enterprise R8 Hardening
# Prevents SnapshotStateList lock verification failure and optimizes Compose runtime.

# Compose Runtime Snapshot Hardening
-keep class androidx.compose.runtime.snapshots.** { *; }
-keepclassmembers class androidx.compose.runtime.snapshots.** { *; }

# Phase 3: Coroutine Stability for Release
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# [SAR] Production Stealth: Strip all Timber logging from release builds
-assumenosideeffects class timber.log.Timber {
    public static void d(...);
    public static void i(...);
    public static void v(...);
    public static void w(...);
}
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keep class androidx.compose.runtime.** { *; }

# Llama.cpp JNI Hardening
-keep class com.scypheon.llama.** { *; }
-keepclassmembers class com.scypheon.llama.** {
    native <methods>;
}

# Room FTS Mapping
-keep class * extends androidx.room.RoomDatabase
-keep class * { @androidx.room.Entity *; }

# Application & DI Hardening
-keep class com.scypheon.app.ScypheonApplication { *; }
-keep class com.scypheon.app.ui.MainActivity { *; }
# LiteRT (TensorFlow Lite) JNI & Core
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.android.gms.tflite.** { *; }
-keep class org.tensorflow.lite.** { *; }

# Hilt & Dagger
-keep class com.scypheon.sdk.core.di.** { *; }
-keep @dagger.hilt.EntryPoint class *
-keep @dagger.Module class *
-keep @javax.inject.Inject class *
