import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import com.skydoves.compose.stability.gradle.StabilityAnalyzerExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
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

ext {
    set("targetInjectProjectName", "composeApp")
    set("krouter.collect.annotations", "org.koin.core.annotation.ModuleProvide")
}

// Analyzer 0.15.0's name-based task selection also includes Wasm library linking
// and Binaryen optimization, which produce no stability reports. Compile sources
// only, including AGP's compileAndroidMain task that its Kotlin-name filter misses.
subprojects {
    pluginManager.withPlugin("com.github.skydoves.compose.stability.analyzer") {
        afterEvaluate {
            val analyzer = extensions.getByType<StabilityAnalyzerExtension>()
            tasks.matching { it.name == "stabilityDump" || it.name == "stabilityCheck" }.configureEach {
                // Reports share a build/stability parent directory with test compilation
                // outputs. Gradle requires ordering when tests are in the same invocation,
                // even with includeTests=false; this does not schedule the tests itself.
                mustRunAfter(tasks.matching {
                    it.name.startsWith("compile") && it.name.contains("Test")
                })
                setDependsOn(tasks.matching { task ->
                    val name = task.name.lowercase()
                    name.startsWith("compile") &&
                            (name.contains("kotlin") || name == "compileandroidmain") &&
                            listOf("sync", "webpack", "executable", "library", "optimize", "link", "assemble")
                                .none { name.contains(it) } &&
                            (analyzer.stabilityValidation.includeTests.get() || !name.contains("test"))
                })
            }
        }
    }
}

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

// 全局配置 JVM target
subprojects {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
}
