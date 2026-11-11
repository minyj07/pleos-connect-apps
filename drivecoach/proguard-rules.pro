# Pleos Connect SDK: keep public API surface (AIDL / Gson models are used across the IPC boundary).
-keep class ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# The Pleos SDKs read IPC payloads with Gson TypeTokens, which need generic signatures; without them the
# Vehicle Service connection fails in minified builds only (same rule as :app, found with its staging build).
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Store policy: don't ship debug/info logs (driving data must not end up in logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
