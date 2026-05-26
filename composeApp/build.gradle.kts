import com.lalilu.gradle.XcodeDetector
import com.lalilu.gradle.commonMainKspDependencies
import com.lalilu.gradle.kspDependenciesForAllTargets
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.stability.analyzer)
}

kotlin {
    androidLibrary {
        namespace = "com.lalilu.app"
        compileSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    XcodeDetector.whenXcodeInstalled {
        listOf(
            iosArm64(),
            iosSimulatorArm64()
        ).forEach {
            it.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
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
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":component"))
            implementation(project(":lmedia:lmedia-core"))
            implementation(project(":lmedia:lmedia-data"))
            implementation(project(":lmedia:lmedia-coil"))
            implementation(project(":lmedia:lmedia-ui"))
            implementation(project(":lplayer"))
            implementation(project(":llyricview"))
            implementation(project(":lhome"))
            implementation(project(":lhistory"))
            implementation(project(":lplaylist"))
            implementation(project(":lalbum"))
            implementation(project(":lartist"))

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
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.sqlite.bundled)
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
    commonMainKspDependencies {
        ksp(libs.koin.compiler)
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.lalilu.lmusic"
            packageVersion = "1.0.0"
            linux {
                modules("jdk.security.auth")
            }
        }
    }
}
