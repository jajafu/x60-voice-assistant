pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        ivy {
            name = "sherpaOnnxOfficialRelease"
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8")
            patternLayout {
                artifact("sherpa-onnx-[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.k2fsa", "sherpa-onnx")
            }
        }
    }
}

rootProject.name = "x60-voice-assistant"
include(":app")
