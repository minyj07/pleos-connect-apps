import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

// Charging-advisor relay server: caches the KECO (한국환경공단) EV charger API and serves
// recommendations to the car app. Runs on a regular JVM 21+, never on Android.
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
    mainClass = "com.example.pleosev.server.ApplicationKt"
    // DC stations only (~25k) fit comfortably; keep headroom for parsing a 9,999-row master page.
    applicationDefaultJvmArgs = listOf("-Xmx384m")
}

tasks.named<JavaExec>("run") {
    // Snapshot goes to server/data/ (gitignored) regardless of where Gradle was started.
    workingDir = layout.projectDirectory.asFile
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test.junit)
}
