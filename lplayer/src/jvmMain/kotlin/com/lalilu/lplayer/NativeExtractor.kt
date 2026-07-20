package com.lalilu.lplayer

import java.util.*

/**
 * 管理 native 库在 Desktop 平台上的加载路径。
 *
 * 构建时 vlc-setup 插件将 VLC 库下载到 src/asset/{os}/vlc/，
 * rococoa/wrapper 等自定义库手动放置在 src/asset/{os}/ 下。
 * CMP 的 appResourcesRootDir 在打包时按 OS 过滤，
 * 并将文件放到安装目录的 resources/ 子目录中。
 *
 * 运行时，[compose.application.resources.dir] 系统属性指向该路径，
 * 此处设置 [jna.library.path]，使得 JNA 能直接加载所有 native 库
 * （VLC 由 vlcj 的 NativeDiscovery 负责，rococoa/wrapper 通过 JNA 自动发现）。
 */
object NativeExtractor {
    const val TAG = "NativeExtractor"
    const val VLC_DIR_NAME = "vlc"

    init {
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        if (resourcesDir != null) {
            System.setProperty("jna.library.path", resourcesDir)
        }
    }

    private val osName: String by lazy {
        System.getProperty("os.name").lowercase(Locale.getDefault())
    }

    fun isMac() = listOf("mac", "darwin").any { osName.contains(it) }
    fun isWin() = listOf("win").any { osName.contains(it) }
}
