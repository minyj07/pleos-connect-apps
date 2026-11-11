import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

// Vehicle plumbing shared by the Pleos Connect apps in this repo: Vehicle SDK error decoding and the
// CarUxRestrictions (driving regulation) monitor. No Pleos SDK dependency: error codes are read reflectively.
android {
    namespace = "com.example.pleos.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    // android.car is an optional platform library. useLibrary() does not propagate to consumers, so every
    // app module depending on this one must call useLibrary("android.car") itself.
    useLibrary("android.car")
}

dependencies {
    api(libs.kotlinx.coroutines.android) // StateFlow is part of the public API
}
