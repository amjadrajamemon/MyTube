import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += "bin"
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
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.documentfile)
    implementation(libs.coil.compose)
    implementation(libs.newpipe.extractor)
    implementation(libs.mlkit.translate)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    testImplementation("junit:junit:4.13.2")
}
