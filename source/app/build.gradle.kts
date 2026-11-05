import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val googleWebClientId = providers.gradleProperty("GOOGLE_WEB_CLIENT_ID")
    .orElse(providers.environmentVariable("GOOGLE_WEB_CLIENT_ID"))
    .orElse("")
    .get()

// Phone builds use Credential Manager + Android authorization, not TV device credentials.
val googleDeviceClientId = ""
val googleDeviceClientSecret = ""

val releaseStoreFilePath = providers.gradleProperty("MYTUBE_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("MYTUBE_RELEASE_STORE_FILE"))
    .orNull
val releaseStorePassword = providers.gradleProperty("MYTUBE_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("MYTUBE_RELEASE_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("MYTUBE_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("MYTUBE_RELEASE_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("MYTUBE_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("MYTUBE_RELEASE_KEY_PASSWORD"))
    .orNull
val productionSigningReady = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

require(googleWebClientId.matches(Regex("[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com"))) {
    "Configure GOOGLE_WEB_CLIENT_ID with the MyTube Web OAuth client ID"
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.drivedeck.tubedeck"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.tubedeck.kxmvd"
        minSdk = 24
        targetSdk = 36
        versionCode = 110
        versionName = "2.3.0"

        testInstrumentationRunner = "com.drivedeck.tubedeck.BlackoutInstrumentation"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "GOOGLE_DEVICE_CLIENT_ID", "\"$googleDeviceClientId\"")
        buildConfigField("String", "GOOGLE_DEVICE_CLIENT_SECRET", "\"$googleDeviceClientSecret\"")
    }

    flavorDimensions += "abi"
    productFlavors {
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters += "arm64-v8a" }
        }
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
        }
    }

    signingConfigs {
        getByName("debug") {
            // Existing test/update channel keeps the pinned v1.5.x certificate.
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (productionSigningReady) {
            create("production") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("production")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep the long-lived update-compatible signing identity without exposing a
            // debuggable production-distribution surface.
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "bin"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        // Match android.compileOptions even when Gradle runs on Android Studio's JDK 21.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    // Direct live-TV protocol engines, aligned to the existing Media3 version.
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.documentfile)
    implementation(libs.coil.compose)
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation(libs.newpipe.extractor)
    implementation(libs.mlkit.translate)
    // Bundled (~0.9 MB) language identification keeps Live Dub auto-detect fully on-device.
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Only the pure Java QR encoder; no ZXing camera/activity/permission dependency.
    implementation("com.google.zxing:core:3.5.3")
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}



