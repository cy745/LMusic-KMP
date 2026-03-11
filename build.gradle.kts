import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.vanniktech.pulish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.stability.analyzer) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.krouter.plugin)
    id("build-logic")
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

// 全局配置项目的kotlin的api和languageVersion
subprojects {
    plugins.withType<KotlinMultiplatformPluginWrapper> {
        configure<KotlinBaseExtension> {
            if (this is HasConfigurableKotlinCompilerOptions<*>) {
                compilerOptions {
                    apiVersion.set(KotlinVersion.KOTLIN_2_2)
                    languageVersion.set(KotlinVersion.KOTLIN_2_2)
                    freeCompilerArgs.set(listOf("-Xcontext-parameters", "-Xexpect-actual-classes"))
                }
            }
        }
    }
}