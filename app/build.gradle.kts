import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Machine-specific values from local.properties (read as a tracked file so the configuration cache stays valid).
val localProperties: Provider<Properties> = providers.fileContents(layout.projectDirectory.file("../local.properties"))
    .asText.map { text -> Properties().apply { load(text.reader()) } }

fun localProperty(key: String, default: String): String =
    localProperties.map { it.getProperty(key, default) }.orElse(default).get()

android {
    namespace = "com.example.pleosev"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.pleosev"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Charging-advisor relay server (:server). Default works with `adb reverse tcp:8080 tcp:8080`;
        // override with advisor.baseUrl in local.properties (e.g. http://10.0.2.2:8080).
        buildConfigField("String", "ADVISOR_BASE_URL", "\"${localProperty("advisor.baseUrl", "http://127.0.0.1:8080")}\"")
    }

    // Release signing key, never committed: release.storeFile / storePassword / keyAlias / keyPassword in
    // local.properties, or RELEASE_STORE_FILE / ..._PASSWORD / RELEASE_KEY_ALIAS / ..._PASSWORD on a build
    // machine. Without it the release APK is built unsigned (the Pleos App Market needs the release key).
    fun signingValue(key: String, env: String) = localProperty(key, providers.environmentVariable(env).orNull.orEmpty())
    val releaseStoreFile = signingValue("release.storeFile", "RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("release.storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("release.keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("release.keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Release builds talk to the deployed server over HTTPS only (empty = advisor disabled).
            val releaseUrl = localProperty("advisor.releaseBaseUrl", "")
            require(releaseUrl.isEmpty() || releaseUrl.startsWith("https://")) { "advisor.releaseBaseUrl must use https" }
            buildConfigField("String", "ADVISOR_BASE_URL", "\"$releaseUrl\"")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // The release build (R8, no debug code or logs) signed with the debug key and pointed at the local
        // relay server, to check minified builds on the emulator. Never distributed.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("String", "ADVISOR_BASE_URL", "\"${localProperty("advisor.baseUrl", "http://127.0.0.1:8080")}\"")
        }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // AAOS car library (CarUxRestrictionsManager etc.) - shipped as an optional platform library.
    useLibrary("android.car")
}

dependencies {
    // Pleos Connect SDK - Vehicle (EvBattery / Odometer / DrivingMode)
    implementation(libs.pleos.vehicle)
    // Pleos Connect SDK - NaviHelper (stock navigation: route state, waypoints, navi screens; NAVI_* permissions)
    implementation(libs.pleos.navihelper)
    // Charging advisor DTOs / geo / scoring shared with the relay server
    implementation(project(":shared"))
    // Vehicle SDK error codes + CarUxRestrictions monitor shared with :drivecoach
    implementation(project(":core-vehicle"))
    // Add when needed:
    // implementation(libs.pleos.fusedlocation)   // vehicle location (FUSED_LOCATION permission)
    // implementation(libs.pleos.tts)             // voice alerts (TTS_SERVICE permission + app registration)
    // implementation(libs.pleos.stt)             // voice input (STT_SERVICE permission + app registration)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Charging advisor: relay-server client and the driver's settings / consent
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.datastore.preferences)

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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
