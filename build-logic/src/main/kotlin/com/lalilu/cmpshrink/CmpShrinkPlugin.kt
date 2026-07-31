package com.lalilu.cmpshrink

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.specs.Spec
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * CMP 资源 shrink 插件（grep 白名单方案）：
 * 扫描源码中的 RemixIcon.<Category>.<Name> 字面量引用，转换为资源文件名，
 * 在 merge*Assets 后删除目标目录中未被引用的资源（remixicon 图标 XML）。
 *
 * 命名规则（已验证 36/36）：
 *   camelCase → snake_case：每个大写字母/数字前插入下划线并转小写
 *   （如 `focus3Line` → `focus_3_line`；`arrowDownSLine` → `arrow_down_s_line`）
 *
 * 安全机制（fail-safe）：
 *   - 源码扫描失败 / 保留列表为空 → 跳过不删（绝不误删）
 *   - 只删除目标目录（默认 remixicon drawable）中不在保留列表的文件
 *
 * 注：与 resignore 插件一致，使用显式接口实现而非 SAM lambda，
 * 以兼容 build-logic 编译环境下的 Action SAM 转换问题。
 */
class CmpShrinkPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("cmpResourceShrink", CmpShrinkExtension::class.java)
        extension.sourceDirs.convention(listOf("src"))
        extension.resourcePrefix.convention("composeResources/com.lalilu.remixicon.generated.resources/drawable")
        extension.enabled.convention(true)

        target.pluginManager.withPlugin("com.android.application") {
            configure(target, extension)
        }
    }

    private fun configure(project: Project, extension: CmpShrinkExtension) {
        if (!extension.enabled.get()) return

        val resourcePrefix = extension.resourcePrefix.get()
        // 引用扫描：递归扫描项目根下所有 .kt 源码（覆盖嵌套模块如 lmedia/lmedia-ui）
        val scanRoot = project.rootDir

        // 扫描前先解析引用（配置阶段执行，失败则禁用插件）
        val keepFiles = collectReferencedResources(scanRoot, resourcePrefix)
        if (keepFiles.isEmpty()) {
            project.logger.warn("[cmpshrink] 未解析到 RemixIcon 引用（扫描根: $scanRoot），跳过资源剔除")
            return
        }
        project.logger.lifecycle("[cmpshrink] 解析到 ${keepFiles.size} 个引用的图标，目标目录: $resourcePrefix")

        val mergeAssetsSpec: Spec<Task> = object : Spec<Task> {
            override fun isSatisfiedBy(element: Task): Boolean =
                element.name.startsWith("merge") && element.name.endsWith("Assets")
        }

        project.tasks.matching(mergeAssetsSpec)
            .configureEach(object : Action<Task> {
                override fun execute(task: Task) {
                    task.doLast(object : Action<Task> {
                        override fun execute(t: Task) {
                            shrinkAssets(t, resourcePrefix, keepFiles)
                        }
                    })
                }
            })
    }

    private fun shrinkAssets(task: Task, resourcePrefix: String, keepFiles: Set<String>) {
        for (root in task.outputs.files.filter { it.isDirectory }) {
            val targetDir: Path = root.toPath().resolve(resourcePrefix)
            if (!Files.isDirectory(targetDir)) continue

            var removed = 0
            var kept = 0
            val removedSample = mutableListOf<String>()

            Files.walk(targetDir).use { stream ->
                stream.asSequence()
                    .filter { Files.isRegularFile(it) }
                    .forEach { file ->
                        val name = file.fileName.toString()
                        if (name in keepFiles) {
                            kept++
                        } else {
                            Files.delete(file)
                            removed++
                            if (removedSample.size < 10) removedSample.add(name)
                        }
                    }
            }

            if (removed > 0) {
                task.project.logger.lifecycle(
                    "[cmpshrink] ${task.name}: 剔除 $removed 个未引用图标，保留 $kept 个 (${if (removed > 10) "示例: " else ""}${removedSample.joinToString(", ")}${if (removed > 10) " ..." else ""})"
                )
            }
        }
    }

    /** 扫描项目根下所有 .kt 源码中的 RemixIcon 引用，返回保留的资源文件名集合。 */
    private fun collectReferencedResources(scanRoot: File, resourcePrefix: String): Set<String> {
        val refPattern = Regex("""RemixIcon\.[A-Za-z0-9_]+\.[A-Za-z0-9_]+""")
        val refs = mutableSetOf<String>()

        // 排除目录：构建产物、工具链、第三方代码
        val excludedDirs = setOf("build", ".gradle", ".git", ".idea", ".claude", "node_modules", "midscene", "build-logic", "release-page", "docs", "kotlin-js-store")

        Files.walk(scanRoot.toPath()).use { stream ->
            stream.asSequence()
                .filter { path ->
                    // 跳过排除目录
                    excludedDirs.none { excluded ->
                        path.toString().split(java.io.File.separatorChar).contains(excluded)
                    }
                }
                .filter { it.fileName.toString().endsWith(".kt") }
                .forEach { file ->
                    val content = Files.readString(file)
                    refPattern.findAll(content).forEach { match ->
                        refs.add(match.value)
                    }
                }
        }

        return refs.mapNotNull { ref ->
            val parts = ref.split(".")
            if (parts.size != 3) return@mapNotNull null
            val category = parts[1]
            val name = parts[2]
            // 命名规则：每个大写/数字前插下划线 + 转小写
            snakeCase(category) + "_" + snakeCase(name) + ".xml"
        }.toSet()
    }

    private fun snakeCase(name: String): String {
        val sb = StringBuilder()
        for (i in name.indices) {
            val ch = name[i]
            if (i > 0 && (ch.isUpperCase() || ch.isDigit())) {
                sb.append('_')
            }
            sb.append(ch.lowercaseChar())
        }
        return sb.toString()
    }
}
