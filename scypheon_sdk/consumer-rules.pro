# SQLCipher native libs & Java bridge
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**

# Room DAOs & Entities
-keep class com.scypheon.sdk.core.**.dao.** { *; }
-keep class com.scypheon.sdk.core.**.entity.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Coroutines & Flow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Safety & Humanitarian Interfaces (prevent obfuscation of public API)
-keep interface com.scypheon.sdk.core.safety.** { *; }
-keep class com.scypheon.sdk.core.safety.** { *; }
-keep class com.scypheon.sdk.core.humanitarian.** { *; }
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Preserve stack traces for crash reporting
-keepattributes SourceFile,LineNumberTable,Signature,Exceptions
-renamesourcefileattribute SourceFile

# Hilt & Dagger generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }

# Kotlin Metadata (reflection/serialization)
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keep class kotlin.Metadata { *; }
