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
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        // Only the forked VLC plugin is hosted on JitPack. Do not query it for
        // Central plugins or their transitive dependencies (CI can hit HTTP 429).
        maven("https://jitpack.io") {
            content {
                includeModule("com.github.cy745", "vlc-setup")
                includeModule("com.github.cy745.vlc-setup", "vlc-setup")
            }
        }
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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.cy745.rococoa") }
        }
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
include(":lplayer:playback-core")
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
include(":lmedia:lmedia-content")
include(":lmedia:lmedia-ui")
include(":lmedia:lmedia-server")
include(":lmedia:lmedia-client")
include(":lmedia:lmedia-coil")

includeBuild("build-logic")
