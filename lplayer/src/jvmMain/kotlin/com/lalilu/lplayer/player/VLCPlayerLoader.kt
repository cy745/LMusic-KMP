package com.lalilu.lplayer.player

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.ReadyState
import com.lalilu.common.ext.readyStateImpl
import com.lalilu.lplayer.NativeExtractor
import com.sun.jna.NativeLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy
import kotlin.coroutines.CoroutineContext

object VLCPlayerLoader : CoroutineScope, ReadyState by readyStateImpl() {
    override val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()

    const val TAG = "VLCPlayerLoader"

    init {
        System.setProperty("vlcj.log", "DEBUG")
    }

    private var initialization: Deferred<Unit>? = null

    @Synchronized
    fun initialize(): Deferred<Unit> {
        initialization?.takeUnless { it.isCancelled }?.let { return it }
        return async {
            try {
                check(NativeDiscovery(MacOsVlcDiscoverer(), DefaultVlcDiscoverer()).discover()) {
                    "VLC native libraries could not be found; check application resources"
                }
                NativeLibrary.getInstance(RuntimeUtil.getLibVlcLibraryName())
                onReady()
            } catch (failure: LinkageError) {
                throw IllegalStateException("VLC native libraries could not be loaded", failure)
            }
        }.also { initialization = it }
    }

}

/**
 * macOS 下 VLC 的发现策略。
 *
 * 继承 [BaseNativeDiscoveryStrategy]，指定 libvlc.dylib 和 libvlccore.dylib 的文件名模式，
 * 并在发现后强制预加载 libvlccore（macOS 需要）。
 */
internal class MacOsVlcDiscoverer(
    private val pluginPathSetter: (String) -> Boolean = {
        LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", it, 1) == 0
    },
) : BaseNativeDiscoveryStrategy(
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
        // BaseNativeDiscoveryStrategy has already expanded "%s/plugins" and
        // checked the directory. Appending again points VLC at plugins/plugins.
        return path?.let(pluginPathSetter) ?: false
    }
}

/**
 * 非 macOS 下 VLC 的默认发现策略（Windows / Linux）。
 */
internal class DefaultVlcDiscoverer(
    private val windows: Boolean = NativeExtractor.isWin(),
    private val resources: () -> String? = { System.getProperty("compose.application.resources.dir") },
    private val pluginPathSetter: (String) -> Boolean = {
        if (NativeExtractor.isWin()) LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=$it") == 0
        else LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", it, 1) == 0
    },
) : BaseNativeDiscoveryStrategy(
    if (windows) arrayOf("libvlc\\.dll", "libvlccore\\.dll")
    else arrayOf("libvlc\\.so(?:\\.\\d+)*", "libvlccore\\.so(?:\\.\\d+)*"),
    arrayOf("%s/plugins", "%s/vlc/plugins"),
) {
    override fun supported(): Boolean = !NativeExtractor.isMac()
    override fun discoveryDirectories(): List<String> = resources()?.let {
        listOf(java.io.File(it, NativeExtractor.VLC_DIR_NAME).path)
    } ?: emptyList()
    override fun setPluginPath(path: String?): Boolean = path?.let(pluginPathSetter) ?: false
}
