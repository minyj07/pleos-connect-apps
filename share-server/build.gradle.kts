import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

// Drive coach "폰으로 보기" server: keeps opaque, end-to-end encrypted report copies for a few days and
// serves the phone viewer page that decrypts them in the browser. Runs on a regular JVM 21+, never on Android.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

application {
    mainClass = "com.example.drivecoach.share.server.ApplicationKt"
    applicationDefaultJvmArgs = listOf("-Xmx128m")
}

tasks.named<JavaExec>("run") {
    // Shares go to share-server/data/ (gitignored) regardless of where Gradle was started.
    workingDir = layout.projectDirectory.asFile
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
