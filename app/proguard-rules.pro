# Pleos Connect SDK: keep public API surface (AIDL / Gson models are used across the IPC boundary).
-keep class ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# The Pleos SDKs read IPC payloads with Gson TypeTokens, which need generic signatures. R8 drops them
# otherwise and the Vehicle Service connection fails in minified builds only ("TypeToken must be created
# with a type argument", found with the staging build on the emulator).
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Release builds keep only warnings and errors in logcat (Pleos app security guide: no debug logs in a
# release). Debug-level advisor logs are already behind BuildConfig.DEBUG; this also drops info logs.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
