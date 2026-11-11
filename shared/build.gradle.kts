import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Code shared by :app (Android) and :server (JVM): API DTOs, geo math, charger code tables, scoring.
// Keep it free of Android and Ktor dependencies. Bytecode is pinned to 17 so the app can consume it
// (the Gradle daemon itself runs on JBR 25).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit)
}
