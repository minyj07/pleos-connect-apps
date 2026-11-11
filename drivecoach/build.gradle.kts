import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Machine-specific values from local.properties (read as a tracked file so the configuration cache stays valid).
val localProperties: Provider<Properties> = providers.fileContents(layout.projectDirectory.file("../local.properties"))
    .asText.map { text -> Properties().apply { load(text.reader()) } }

fun localProperty(key: String, default: String): String =
    localProperties.map { it.getProperty(key, default) }.orElse(default).get()

// "폰으로 보기" share server (:share-server). Debug/staging upload to the PC from the emulator (10.0.2.2) and may
// take the phone address from the server (a demo tunnel); share.viewBaseUrl pins it instead.
fun com.android.build.api.dsl.VariantDimension.localShareServer() {
    buildConfigField("String", "SHARE_BASE_URL", "\"${localProperty("share.baseUrl", "http://10.0.2.2:8090")}\"")
    buildConfigField("String", "SHARE_VIEW_BASE_URL", "\"${localProperty("share.viewBaseUrl", "")}\"")
    buildConfigField("boolean", "SHARE_TRUST_SERVER_VIEW_URL", "true")
}

android {
    namespace = "com.example.drivecoach"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.drivecoach" // placeholder: replace with the real package before submission
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        localShareServer()
    }

    buildTypes {
        release {
            // Release: one fixed https address for uploads and for the phone links (empty = feature hidden).
            val releaseUrl = localProperty("share.releaseBaseUrl", "")
            require(releaseUrl.isEmpty() || releaseUrl.startsWith("https://")) { "share.releaseBaseUrl must use https" }
            buildConfigField("String", "SHARE_BASE_URL", "\"$releaseUrl\"")
            buildConfigField("String", "SHARE_VIEW_BASE_URL", "\"$releaseUrl\"")
            buildConfigField("boolean", "SHARE_TRUST_SERVER_VIEW_URL", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release code and R8, signed with the debug key so it installs on the emulator (catches minify-only bugs).
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            localShareServer()
        }
    }

    // staging runs the release-only sources (real vehicle signals, no demo drives).
    sourceSets.getByName("staging").kotlin.directories += "src/release/java"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // AAOS car library (CarPropertyManager, CarUxRestrictionsManager) - optional platform library.
    useLibrary("android.car")
}

dependencies {
    implementation(project(":core-vehicle"))

    // Pleos Connect SDK - Vehicle (SteeringWheel / Brake / DrivingMode / Odometer)
    implementation(libs.pleos.vehicle)
    implementation(libs.kotlinx.serialization.json)
    // "폰으로 보기" QR codes (encoder only)
    implementation(libs.zxing.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
