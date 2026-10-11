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
 *     releaseOnly.set(true)           // 只在 release 变体生效（默认）
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

    /**
     * 是否只在 release 变体生效，默认 `true`。
     *
     * 为 `true` 时只挂钩 `mergeReleaseAssets` / `merge<Flavor>ReleaseAssets` 之类的任务，
     * debug 等非 release 变体既不扫描源码也不剔除资源；
     * 设为 `false` 则所有 `merge*Assets` 任务都会剔除（旧行为，不推荐）。
     */
    abstract val releaseOnly: Property<Boolean>
}
