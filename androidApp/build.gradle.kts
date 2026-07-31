import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.streams.asSequence

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    id("com.lalilu.resignore")
}

val keystoreProps = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { Properties().apply { load(it.inputStream()) } }

android {
    namespace = "com.lalilu.lmusic"
    compileSdk = libs.versions.android.targetSdk.get().toInt()
    // 显式指定 NDK 版本：未配置时 AGP 用默认版本解析 NDK handler，
    // 版本不匹配会导致 stripDebugSymbols 静默降级（.so 带 debug 符号进包）
    ndkVersion = libs.versions.android.ndk.get()

    if (keystoreProps != null) {
        val storeFileValue = keystoreProps["storeFile"]?.toString() ?: ""
        val storePasswordValue = keystoreProps["storePassword"]?.toString() ?: ""
        val keyAliasValue = keystoreProps["keyAlias"]?.toString() ?: ""
        val keyPasswordValue = keystoreProps["keyPassword"]?.toString() ?: ""

        if (storeFileValue.isNotBlank() && file(storeFileValue).exists()) {
            signingConfigs.create("release") {
                storeFile = file(storeFileValue)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    defaultConfig {
        applicationId = "com.lalilu.lmusic.kmp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            merges += "/META-INF/services/**"
            pickFirsts += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "/META-INF/INDEX.LIST"
            pickFirsts += "/META-INF/io.netty.versions.properties"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-android.pro"
            )

            signingConfig = runCatching { signingConfigs["release"] }.getOrNull()
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

/**
 * UPX 二次压缩（实验性）：在 AGP strip 之后对 .so 执行 upx --android-shlib --lzma。
 * 顺序关键：必须先 strip 再 UPX（UPX 压缩后再 strip 会破坏压缩结构）。
 * 风险：Android 15+ 16KB 页面设备上 UPX 压缩 so 有崩溃报告（upx/upx#18870），
 *      需真机验证；可通过 -Plalilu.upx.enabled=false 一键关闭。
 */
val upxEnabled = providers.gradleProperty("lalilu.upx.enabled").orNull != "false"
if (upxEnabled) {
    // strip 任务在 variant 创建后注册，用 matching 延迟查找
    tasks.matching { it.name == "stripReleaseDebugSymbols" }.configureEach {
        doLast {
            val strippedRoot = outputs.files.singleFile.toPath()
            Files.walk(strippedRoot).use { stream ->
                stream.asSequence()
                    .filter { Files.isRegularFile(it) && it.fileName.toString() == "libtag.so" }
                    .forEach { so ->
                        compressWithUpx(so)
                    }
            }
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.koin.androidx.startup)
    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs)
    implementation(libs.coil.gif)
    implementation(libs.coil.compose)
    implementation(libs.settings.no.arg)
    implementation(libs.compose.preview)
}

private fun compressWithUpx(so: Path) {
    val sizeBefore = Files.size(so)
    try {
        // UPX 要求文件可执行
        so.toFile().setExecutable(true, false)
        val process = ProcessBuilder("upx", "--android-shlib", "--lzma", so.toString())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            val sizeAfter = Files.size(so)
            logger.lifecycle(
                "[upx] $so: ${sizeBefore / 1024}KB -> ${sizeAfter / 1024}KB (省 ${(sizeBefore - sizeAfter) / 1024}KB)"
            )
        } else {
            logger.warn("[upx] 压缩失败 (exit=$exitCode): ${output.lines().lastOrNull()}")
        }
    } catch (e: Exception) {
        logger.warn("[upx] 压缩异常: $so (${e.message})")
    }
}
