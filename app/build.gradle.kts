import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jajafu.x60assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jajafu.x60assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "1.0.0-rc26"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "com.jajafu.x60assistant.KeystoreInstrumentation"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

val bundledXAsrArchive = file("src/main/assets/models/asr/sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03.tar.bz2")
val verifyBundledXAsr by tasks.registering {
    doLast {
        check(bundledXAsrArchive.isFile) {
            "Missing bundled X-ASR archive. Run scripts/prepare-bundled-xasr.ps1 before building."
        }
        check(bundledXAsrArchive.length() == 136_396_739L) { "Bundled X-ASR archive has the wrong size." }
        val digest = MessageDigest.getInstance("SHA-256")
        bundledXAsrArchive.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        check(hash == "5d02c36d7b44e886b7c8f0d8e051f8713acab96c264bb6ef9e718be39a6a2224") {
            "Bundled X-ASR archive failed SHA-256 verification."
        }
    }
}

tasks.named("preBuild") { dependsOn(verifyBundledXAsr) }
