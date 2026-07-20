package com.lalilu.lplayer.player

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.ReadyState
import com.lalilu.common.ext.readyStateImpl
import com.lalilu.lplayer.NativeExtractor
import com.sun.jna.NativeLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import kotlin.coroutines.CoroutineContext

object VLCPlayerLoader : CoroutineScope, ReadyState by readyStateImpl() {
    override val coroutineContext: CoroutineContext = Dispatchers.IO

    const val TAG = "VLCPlayerLoader"

    init {
        System.setProperty("vlcj.log", "DEBUG")
    }

    fun initialize() = launch {
        Logger.i(tag = TAG, messageString = "start Initialize")

        val strategies = arrayOf(
            MacOsVlcDiscoverer(),
            DefaultVlcDiscoverer()
        )

        NativeDiscovery(*strategies).discover()

        Logger.i("NativeDiscovery completed")
        onReady()
    }
}

/**
 * macOS 下 VLC 的发现策略。
 *
 * 继承 [BaseNativeDiscoveryStrategy]，指定 libvlc.dylib 和 libvlccore.dylib 的文件名模式，
 * 并在发现后强制预加载 libvlccore（macOS 需要）。
 */
private class MacOsVlcDiscoverer : BaseNativeDiscoveryStrategy(
    arrayOf("libvlc\\.dylib", "libvlccore\\.dylib"),
    arrayOf("%s/plugins")
) {
    override fun supported(): Boolean = NativeExtractor.isMac()

    override fun discoveryDirectories(): List<String> {
        val resourcesDir = System.getProperty("compose.application.resources.dir") ?: return emptyList()
        return listOf("$resourcesDir/${NativeExtractor.VLC_DIR_NAME}")
    }

    override fun onFound(path: String?): Boolean {
        Logger.i(tag = VLCPlayerLoader.TAG, messageString = "VLC libraries found at: $path")
        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), path)
        NativeLibrary.getInstance(RuntimeUtil.getLibVlcCoreLibraryName())
        return true
    }

    override fun setPluginPath(path: String?): Boolean {
        val pluginsPath = "$path/plugins"
        return LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", pluginsPath, 1) == 0
    }
}

/**
 * 非 macOS 下 VLC 的默认发现策略（Windows / Linux）。
 */
private class DefaultVlcDiscoverer : NativeDiscoveryStrategy {
    override fun supported(): Boolean = !NativeExtractor.isMac()

    override fun discover(): String? {
        val resourcesDir = System.getProperty("compose.application.resources.dir") ?: return null
        return "$resourcesDir/${NativeExtractor.VLC_DIR_NAME}"
    }

    override fun onFound(path: String?): Boolean {
        Logger.i(tag = VLCPlayerLoader.TAG, messageString = "VLC libraries found at: $path")
        return true
    }

    override fun onSetPluginPath(path: String?): Boolean = true
}
