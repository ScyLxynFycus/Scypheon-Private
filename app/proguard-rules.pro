# Scypheon Private: Enterprise R8 Hardening
# Prevents SnapshotStateList lock verification failure and optimizes Compose runtime.

-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateList { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-dontoptimize
-keep class androidx.compose.runtime.** { *; }

# Llama.cpp JNI Hardening
-keep class com.scypheon.llama.** { *; }
-keepclassmembers class com.scypheon.llama.** {
    native <methods>;
}

# Room FTS Mapping
-keep class * extends androidx.room.RoomDatabase
-keep class * { @androidx.room.Entity *; }
