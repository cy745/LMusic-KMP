import com.lalilu.gradle.kspDependenciesForAllTargets
import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupMultiplatform
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.stability.analyzer)
    alias(libs.plugins.vlcSetup)
}

val appResourcesPath: java.io.File = rootDir.resolve("lplayer/src/jvmMain/assets")

vlcSetup {
    vlcVersion = "3.0.21"
    shouldCompressVlcFiles = true
    shouldIncludeAllVlcFiles = false
    pathToCopyVlcLinuxFilesTo = appResourcesPath.resolve("linux/vlc")
    pathToCopyVlcMacosFilesTo = appResourcesPath.resolve("macos/vlc")
    pathToCopyVlcWindowsFilesTo = appResourcesPath.resolve("windows/vlc")
}

kotlin {
    setupMultiplatform(
        setupAndroidTarget = {
            namespace = "com.lalilu.app"
            packaging {
                resources {
                    merges += "/META-INF/services/**"
                    pickFirsts += "/META-INF/{AL2.0,LGPL2.1}"
                    pickFirsts += "/META-INF/INDEX.LIST"
                    pickFirsts += "/META-INF/io.netty.versions.properties"
                }
            }
        },
        setupWasmTarget = {
            outputModuleName.set("composeApp")
            browser {
                val rootDirPath = project.rootDir.path
                val projectDirPath = project.projectDir.path
                commonWebpackConfig {
                    outputFileName = "composeApp.js"
                    devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static = (static ?: mutableListOf()).apply {
                            add(rootDirPath)
                            add(projectDirPath)
                        }
                        client = KotlinWebpackConfig.DevServer.Client(
                            overlay = KotlinWebpackConfig.DevServer.Client.Overlay(
                                errors = false,
                                warnings = false
                            )
                        )
                    }
                }
            }
            binaries.executable()
        },
        setupIosTarget = {
            forEach {
                it.binaries.framework {
                    baseName = "ComposeApp"
                    isStatic = true
                }
            }
        }
    )
    jvmToolchain(21)
    setupKoin()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":component"))
            implementation(project(":lmedia:lmedia-domain"))
            implementation(project(":lmedia:lmedia-core"))
            implementation(project(":lmedia:lmedia-data"))
            implementation(project(":lmedia:lmedia-coil"))
            implementation(project(":lmedia:lmedia-ui"))
            implementation(project(":lmedia:lmedia-client"))
            implementation(project(":lplayer"))
            implementation(project(":llyricview"))
            implementation(project(":lhome"))
            implementation(project(":lhistory"))
            implementation(project(":lplaylist"))
            implementation(project(":lalbum"))
            implementation(project(":lartist"))
            implementation(project(":lsettings"))

            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.material)
            implementation(libs.compose.resources)
            implementation(libs.compose.preview)
            implementation(libs.room3.paging)

            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.sqlite.bundled)
            implementation(libs.koin.test)
            implementation(libs.koin.test.junit4)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.sqlite.bundled)
        }
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        webMain.dependencies {
            implementation(libs.sqlite.web)
        }
    }

    kspDependenciesForAllTargets {
        ksp(libs.room3.compiler)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

compose.desktop {
    application {
        mainClass = "com.lalilu.lmusic.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from("proguard-desktop.pro")
        }

        nativeDistributions {
            appResourcesRootDir.set(appResourcesPath)
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.lalilu.lmusic"
            packageVersion = "1.0.0"
            linux {
                modules("jdk.security.auth")
                iconFile.set(project.layout.projectDirectory.file("src/main/resources/icons/icon-linux-512.png"))
            }
            macOS {
                iconFile.set(project.layout.projectDirectory.file("src/main/resources/icons/icon-mac.icns"))
                bundleID = "com.lalilu.lmusic"
                appCategory = "public.app-category.music"

                // 条件签名 + 公证：仅在 CI 环境变量存在时启用
                // fork 用户不设这些变量则走无签名打包，不受影响
                val signIdentity = providers.environmentVariable("APPLE_SIGN_IDENTITY").orNull
                val appleIdEnv = providers.environmentVariable("APPLE_ID").orNull
                val applePwdEnv = providers.environmentVariable("APPLE_ID_PASSWORD").orNull
                val teamIdEnv = providers.environmentVariable("APPLE_TEAM_ID").orNull

                if (signIdentity != null && appleIdEnv != null && applePwdEnv != null && teamIdEnv != null) {
                    signing {
                        sign.set(true)
                        identity.set(signIdentity)
                    }
                    notarization {
                        appleID.set(appleIdEnv)
                        password.set(applePwdEnv)
                        teamID.set(teamIdEnv)
                    }
                }
            }
            windows {
                iconFile.set(project.layout.projectDirectory.file("src/main/resources/icons/icon-windows.ico"))
            }
        }
    }
}
