import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.va.voiceassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.va.voiceassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 42
        versionName = "1.0.8"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "com.va.voiceassistant.KeystoreInstrumentation"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "onnx"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Release APK must bundle the KWS and Silero VAD model assets. These binaries are gitignored
// and prepared by scripts/prepare-*.ps1; without them the service fails immediately at startup
// (KWS_MODEL_INVALID / VAD_INIT_FAILED), which is how the v1.0.6 release APK broke.
// Guard only the release path so test/lint/assembleDebug stay unaffected.
val requiredReleaseModelAssets = listOf(
    "models/vad/silero_vad.onnx",
    "models/kws/**/encoder-*.int8.onnx",
    "models/kws/**/decoder-*.onnx",
    "models/kws/**/joiner-*.int8.onnx",
    "models/kws/**/tokens.txt",
    "models/kws/**/keywords.txt",
)

val verifyReleaseModelAssets = tasks.register("verifyReleaseModelAssets") {
    group = "verification"
    description = "Fails when bundled KWS/VAD model assets are missing from a release build."
    val assets = fileTree(layout.projectDirectory.dir("src/main/assets"))
    val required = requiredReleaseModelAssets
    doLast {
        val missing = required.filter { pattern -> assets.matching { include(pattern) }.isEmpty }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing bundled model assets for release: ${missing.joinToString()}. " +
                    "Run scripts\\prepare-m1-kws-assets.ps1 and scripts\\prepare-vad-asset.ps1 first.",
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseModelAssets)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.sherpa.onnx) {
        artifact {
            type = "aar"
        }
    }
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.commons.compress)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.session)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
