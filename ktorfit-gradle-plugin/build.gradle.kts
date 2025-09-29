// my-plugin/build.gradle.kts
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`  // 提供插件开发支持
}

gradlePlugin {
    // 注册插件 ID 和实现类
    plugins {
        create("ktorfit-gradle-plugin") {
            id = "com.lalilu.ktorfit"
            implementationClass = "com.lalilu.gradle.ktorfit.KtorfitGradlePlugin"
        }
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
}