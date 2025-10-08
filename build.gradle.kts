import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.vanniktech.pulish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.krouter.plugin)
}

// 配置注入遍历的起点项目
ext { set("targetInjectProjectName", "composeApp") }

// 子项目都开启 wasm-kclass-fqn
// 让 wasm 支持直接访问 KClass 的 qualifiedName 参数
subprojects {
    tasks.withType<KotlinJsCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xwasm-kclass-fqn")
    }
}