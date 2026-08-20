rootProject.name = "LMusic-KMP"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://jitpack.io")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "ir.mahozad.vlc-setup") {
                useModule("com.github.cy745:vlc-setup:${requested.version}")
            }
        }
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
        mavenLocal()
        mavenCentral()
        maven("https://jitpack.io")
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

include(":androidApp")
include(":composeApp")
include(":common")
include(":component")
include(":lplayer")
include(":lplayer:libwrapper")
include(":lplayer:lib-decoder-flac")
include(":llyric")
include(":llyricview")
include(":lhome")
include(":lhistory")
include(":lplaylist")
include(":lalbum")
include(":lartist")
include(":lsearch")
include(":lsettings")
include(":lfont")

include("lmedia-server")
include(":lmedia:lmedia-data")
include(":lmedia:lmedia-core")
include(":lmedia:lmedia-domain")
include(":lmedia:lmedia-ui")
include(":lmedia:lmedia-server")
include(":lmedia:lmedia-client")
include(":lmedia:lmedia-coil")

includeBuild("build-logic")
