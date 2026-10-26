import com.lalilu.gradle.setupMultiplatform

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}
group = "com.lalilu.lmedia.content"
kotlin {
    setupMultiplatform(enableAndroidResources = false)
    sourceSets {
        commonMain.dependencies {
            api(project(":lmedia:lmedia-domain"))
            implementation(libs.kotlinx.io)
            implementation(kotlincrypto.hash.sha2)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
