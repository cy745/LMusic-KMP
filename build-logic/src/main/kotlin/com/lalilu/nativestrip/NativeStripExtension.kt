package com.lalilu.nativestrip

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * [com.lalilu.nativestrip.NativeStripPlugin] 的扩展配置。
 *
 * 用法：
 * ```kotlin
 * nativeStrip {
 *     stripTargets.set(listOf("libtag.so"))
 *     enabled.set(true)
 * }
 * ```
 */
abstract class NativeStripExtension {

    /**
     * 需要 strip 的 .so 文件名列表（匹配 merge 产物中的文件名），
     * 默认 `["libtag.so"]`。
     */
    abstract val stripTargets: ListProperty<String>

    /** 是否启用 strip，默认 `true`。设置 `false` 可一键回退。 */
    abstract val enabled: Property<Boolean>
}
