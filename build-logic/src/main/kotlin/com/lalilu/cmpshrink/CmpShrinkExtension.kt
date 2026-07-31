package com.lalilu.cmpshrink

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * [com.lalilu.cmpshrink.CmpShrinkPlugin] 的扩展配置。
 *
 * 用法：
 * ```kotlin
 * cmpResourceShrink {
 *     sourceDirs.set(listOf(...))     // 扫描源码目录（默认项目全部模块）
 *     resourcePrefix.set("composeResources/com.lalilu.remixicon.generated.resources/drawable")
 *     enabled.set(true)
 * }
 * ```
 */
abstract class CmpShrinkExtension {

    /**
     * 扫描的源码目录列表（包含 .kt 文件的目录），
     * 默认扫描项目根下所有直接子目录的 src 目录。
     */
    abstract val sourceDirs: ListProperty<String>

    /**
     * 目标资源目录前缀（APK 内相对路径），
     * 默认 remixicon 图标的 composeResources 目录。
     */
    abstract val resourcePrefix: Property<String>

    /** 是否启用，默认 `true`。设置 `false` 可一键关闭。 */
    abstract val enabled: Property<Boolean>
}
