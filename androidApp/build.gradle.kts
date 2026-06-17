import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val keystoreProps = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { Properties().apply { load(it.inputStream()) } }

android {
    namespace = "com.lalilu.lmusic"
    compileSdk = libs.versions.android.targetSdk.get().toInt()

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
            isDebuggable = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-android.pro"
            )

            signingConfig = runCatching { signingConfigs["release"] }.getOrNull()
                ?: signingConfigs.getByName("debug")
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
