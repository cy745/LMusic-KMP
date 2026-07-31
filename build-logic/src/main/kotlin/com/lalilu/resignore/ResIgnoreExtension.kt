package com.lalilu.resignore

import org.gradle.api.file.RegularFileProperty

/**
 * [com.lalilu.resignore.ResIgnorePlugin] 的扩展配置。
 *
 * 用法：
 * ```kotlin
 * resIgnore {
 *     resIgnoreFile.set(layout.projectDirectory.file("config/ignore.txt"))
 * }
 * ```
 */
abstract class ResIgnoreExtension {

    /**
     * `.resignore` 文件路径，默认 `<project>/.resignore`。
     * 文件不存在时插件跳过剔除并输出警告。
     */
    abstract val resIgnoreFile: RegularFileProperty
}
