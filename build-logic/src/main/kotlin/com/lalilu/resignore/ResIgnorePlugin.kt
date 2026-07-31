package com.lalilu.resignore

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.specs.Spec
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.streams.asSequence

/**
 * 资源黑名单插件：读取 `.resignore` 文件（语法参考 `.gitignore`），
 * 在打包前从 APK 中剔除声明为"不需要"的资源。
 *
 * 双通道实现（AGP 9.0 已验证）：
 * - `packaging.resources.excludes` 只对 Java resources 生效（META-INF/、org/、根级文件），
 *   对 `assets/` 下的文件无效（实验验证）。
 * - `assets/` 前缀的 pattern 走 `merge*Assets` 任务后置删除通道。
 *
 * `.resignore` 语法：
 * - 每行一个 glob 模式，匹配 APK 内路径（APK 根为基准，如 `org/fusesource/jansi/` 目录下全部文件）
 * - `#` 开头为注释，空行忽略
 * - `assets/` 前缀的模式走 assets 剔除通道，其余走 Java resources 剔除通道
 *
 * 注：本插件使用显式接口实现而非 SAM lambda（object : Action { ... }），
 * 以兼容 build-logic 编译环境下的 Action SAM 转换问题。
 */
class ResIgnorePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("resIgnore", ResIgnoreExtension::class.java)
        extension.resIgnoreFile.convention(target.layout.projectDirectory.file(".resignore"))

        // 仅在 Android application 插件应用后配置（packaging DSL 来自 AGP）
        target.pluginManager.withPlugin("com.android.application") {
            configure(target, extension)
        }
    }

    private fun configure(project: Project, extension: ResIgnoreExtension) {
        val ignoreFile = extension.resIgnoreFile.asFile.orNull
        if (ignoreFile == null || !ignoreFile.exists()) {
            project.logger.warn("[resignore] ${ignoreFile?.path ?: ".resignore"} 不存在，跳过资源剔除")
            return
        }

        val patterns = parseIgnoreFile(ignoreFile)
        if (patterns.isEmpty()) {
            project.logger.warn("[resignore] ${ignoreFile.path} 为空或只有注释，跳过资源剔除")
            return
        }

        // 双通道分类：assets/ 前缀 → assets 删除通道；其余 → Java resources 通道
        val assetsPatterns = patterns.filter { it.startsWith("assets/") }
        val javaResourcePatterns = patterns.filterNot { it.startsWith("assets/") }

        if (javaResourcePatterns.isNotEmpty()) {
            configureJavaResourceExcludes(project, javaResourcePatterns)
        }

        if (assetsPatterns.isNotEmpty()) {
            configureAssetsDeletion(project, assetsPatterns)
        }
    }

    private fun configureJavaResourceExcludes(project: Project, patterns: List<String>) {
        val app = project.extensions.getByType(ApplicationExtension::class.java)
        app.packaging.resources.excludes.addAll(patterns)
        project.logger.lifecycle("[resignore] Java resources 排除规则: $patterns")
    }

    private fun configureAssetsDeletion(project: Project, patterns: List<String>) {
        val matchers = patterns.map { compileGlob(it) }

        val assetsTaskSpec: Spec<Task> = object : Spec<Task> {
            override fun isSatisfiedBy(element: Task): Boolean =
                element.name.startsWith("merge") && element.name.endsWith("Assets")
        }

        project.tasks.matching(assetsTaskSpec)
            .configureEach(object : Action<Task> {
                override fun execute(task: Task) {
                    task.doLast(object : Action<Task> {
                        override fun execute(t: Task) {
                            val outputDir = t.outputs.files.singleFile.toPath()
                            val removed = mutableListOf<String>()

                            Files.walk(outputDir).use { stream ->
                                stream.asSequence()
                                    .filter { Files.isRegularFile(it) }
                                    .filter { file ->
                                        val relative = outputDir.relativize(file)
                                        matchers.any { it.matches(relative) }
                                    }
                                    .forEach { file ->
                                        Files.delete(file)
                                        removed.add(outputDir.relativize(file).toString())
                                    }
                            }

                            if (removed.isNotEmpty()) {
                                val sample = removed.take(10).joinToString(", ")
                                project.logger.lifecycle(
                                    "[resignore] ${t.name}: 剔除 ${removed.size} 个 assets 文件 (${if (removed.size > 10) "示例: " else ""}$sample${if (removed.size > 10) " ..." else ""})"
                                )
                            }
                        }
                    })
                }
            })
    }

    private fun parseIgnoreFile(file: File): List<String> =
        file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun compileGlob(pattern: String): PathMatcher =
        FileSystems.getDefault().getPathMatcher("glob:${pattern.replace('/', File.separatorChar)}")
}
