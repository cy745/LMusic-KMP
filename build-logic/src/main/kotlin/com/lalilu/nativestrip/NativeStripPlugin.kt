package com.lalilu.nativestrip

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
 * 原生库 strip 插件：在打包前对 merge 产物中的 .so 执行 `llvm-strip --strip-all`。
 *
 * 背景：AGP 只对 `externalNativeBuild`（CMake/ndk-build）构建的 .so 执行 strip，
 * 对各 sourceSet 的 `jniLibs/` 目录下的预编译 .so 不做处理。若预编译 .so 未 strip，
 * 会携带 debug 符号进 APK（实测 libtag.so 16.4MB 中 76% 为符号信息）。
 *
 * 本插件挂载 `merge*JniLibs` 任务后置执行（与 resignore 插件同模式），
 * strip 只删除静态符号表和 debug 节（.symtab/.debug_*），
 * 保留 .dynsym 动态符号，不影响 dlopen/符号解析，零运行风险。
 *
 * 注：与 resignore 插件一致，使用显式接口实现而非 SAM lambda，
 * 以兼容 build-logic 编译环境下的 Action SAM 转换问题。
 */
class NativeStripPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("nativeStrip", NativeStripExtension::class.java)
        extension.stripTargets.convention(listOf("libtag.so"))
        extension.enabled.convention(true)

        // 挂载 jniLibs 合并任务（androidApp 中合并所有依赖库的 .so）
        target.pluginManager.withPlugin("com.android.application") {
            configure(target, extension)
        }
    }

    private fun configure(project: Project, extension: NativeStripExtension) {
        if (!extension.enabled.get()) return

        val stripTargets = extension.stripTargets.get()
        val ndkStrip = locateNdkStrip(project)
        if (ndkStrip == null) {
            project.logger.warn("[nativestrip] 未找到 NDK llvm-strip，跳过 .so strip（可通过 nativeStrip.enabled=false 关闭）")
            return
        }
        project.logger.lifecycle("[nativestrip] 使用 $ndkStrip，目标: $stripTargets")

        // AGP 8 任务名 mergeReleaseJniLibs；AGP 9 更名为 mergeReleaseNativeLibs
        val jniLibsTaskSpec: Spec<Task> = object : Spec<Task> {
            override fun isSatisfiedBy(element: Task): Boolean =
                element.name.startsWith("merge") &&
                    (element.name.endsWith("NativeLibs") || element.name.endsWith("JniLibs"))
        }

        project.tasks.matching(jniLibsTaskSpec)
            .configureEach(object : Action<Task> {
                override fun execute(task: Task) {
                    task.doLast(object : Action<Task> {
                        override fun execute(t: Task) {
                            // AGP 的 merge 任务输出可能包含多个目录/文件，遍历所有输出目录
                            val outputRoots: List<Path> = t.outputs.files
                                .filter { it.isDirectory }
                                .map { it.toPath() }
                            var strippedCount = 0
                            var skipped = 0

                            for (jniRoot in outputRoots) {
                                Files.walk(jniRoot).use { stream ->
                                    stream.asSequence()
                                        .filter { Files.isRegularFile(it) }
                                        .filter { it.fileName.toString() in stripTargets }
                                        .forEach { so ->
                                            if (stripLibrary(project, ndkStrip, so)) {
                                                strippedCount++
                                            } else {
                                                skipped++
                                            }
                                        }
                                }
                            }

                            if (strippedCount > 0) {
                                project.logger.lifecycle(
                                    "[nativestrip] ${t.name}: strip 完成 $strippedCount 个 .so${if (skipped > 0) "，$skipped 个失败跳过" else ""}"
                                )
                            }
                        }
                    })
                }
            })
    }

    private fun stripLibrary(project: Project, ndkStrip: String, so: Path): Boolean {
        val sizeBefore = Files.size(so)
        return try {
            val process = ProcessBuilder(ndkStrip, "--strip-all", so.toString())
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val sizeAfter = Files.size(so)
                project.logger.lifecycle(
                    "[nativestrip] $so: ${sizeBefore / 1024}KB -> ${sizeAfter / 1024}KB (省 ${(sizeBefore - sizeAfter) / 1024}KB)"
                )
                true
            } else {
                project.logger.warn("[nativestrip] strip 失败 (exit=$exitCode): $so")
                false
            }
        } catch (e: Exception) {
            project.logger.warn("[nativestrip] strip 异常: $so (${e.message})")
            false
        }
    }

    /** 定位 NDK 的 llvm-strip：local.properties sdk.dir / ANDROID_HOME / macOS 默认路径。 */
    private fun locateNdkStrip(project: Project): String? {
        val sdkDir = resolveSdkDir(project) ?: return null
        val ndkRoot = File(sdkDir, "ndk")
        if (!ndkRoot.isDirectory) return null

        val hostDir = when {
            System.getProperty("os.name").lowercase().contains("mac") ->
                if (System.getProperty("os.arch").lowercase().contains("aarch64")) "darwin-arm64" else "darwin-x86_64"
            System.getProperty("os.name").lowercase().contains("linux") -> "linux-x86_64"
            else -> "windows-x86_64"
        }

        val preferredHost = hostDir
        return ndkRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name } // 取最新 NDK 版本
            ?.firstNotNullOfOrNull { ndk ->
                val prebuilt = File(ndk, "toolchains/llvm/prebuilt")
                // 优先匹配当前 host；否则 fallback 任意 prebuilt 目录
                // （NDK 的 llvm 工具是 universal binary，跨架构可运行）
                val hostDir = prebuilt.listFiles()
                    ?.firstOrNull { it.name == preferredHost }
                    ?: prebuilt.listFiles()?.firstOrNull()
                hostDir?.let { File(it, "bin/llvm-strip") }?.takeIf { it.isFile }?.absolutePath
            }
    }

    private fun resolveSdkDir(project: Project): File? {
        // 1. local.properties 中的 sdk.dir
        val localProps = project.rootProject.file("local.properties")
        if (localProps.exists()) {
            val sdkDir = localProps.readLines()
                .firstOrNull { it.startsWith("sdk.dir") }
                ?.substringAfter("=")
                ?.trim()
                ?.replace("\\:", ":")
            if (sdkDir != null) return File(sdkDir)
        }
        // 2. 环境变量
        System.getenv("ANDROID_HOME")?.let { return File(it) }
        System.getenv("ANDROID_SDK_ROOT")?.let { return File(it) }
        // 3. macOS 默认路径
        File(System.getProperty("user.home"), "Library/Android/sdk").takeIf { it.isDirectory }?.let { return it }
        return null
    }
}
