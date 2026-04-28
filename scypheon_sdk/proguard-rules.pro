-keep class android.llama.cpp.LLamaAndroid {
    native <methods>;
}
-keep class android.llama.cpp.LLamaAndroid$State { *; }
-keep class android.llama.cpp.LLamaAndroid$IntVar { *; }
-keep class java.lang.invoke.StringConcatFactory { *; }
-dontwarn java.lang.invoke.StringConcatFactory
-keep class com.scypheon.sdk.** { *; }
-dontwarn javax.lang.model.**
-dontwarn com.scypheon.sdk.**
