rootProject.name = "LMusic-KMP"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://androidx.dev/kmp/builds/15009014/artifacts/snapshots/repository")
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://androidx.dev/kmp/builds/15009014/artifacts/snapshots/repository")
    }

    versionCatalogs {
        create("kotlincrypto") {
            // https://github.com/KotlinCrypto/version-catalog/blob/master/gradle/kotlincrypto.versions.toml
            from("org.kotlincrypto:version-catalog:0.7.2")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
include(":common")
include(":component")
include(":lplayer")
include(":lplayer:libwrapper")
include(":lplayer:lib-decoder-flac")
include(":llyric")
include(":llyricview")
include(":lhome")

include("lmedia-server")
include(":lmedia:lmedia-core")
include(":lmedia:lmedia-ui")
include(":lmedia:lmedia-server")
include(":lmedia:lmedia-client")
include(":lmedia:lmedia-coil")

include(":thirdparty:navigation3-ui")

includeBuild("build-logic")