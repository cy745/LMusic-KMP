package com.lalilu.lplayer.player

import com.lalilu.common.ext.ReadyState
import com.lalilu.common.ext.io
import com.lalilu.common.ext.readyStateImpl
import com.lalilu.lplayer.NativeExtractor
import com.sun.jna.NativeLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import kotlin.coroutines.CoroutineContext

object VLCPlayerLoader : CoroutineScope, ReadyState by readyStateImpl() {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val logger = KotlinLogging.logger(TAG)

    const val TAG = "VLCPlayerLoader"

    init {
        System.setProperty("vlcj.log", "DEBUG")
    }

    fun initialize(forceOverride: Boolean = false) = launch {
        logger.info { "start Initialize" }

        NativeExtractor.doExtract(forceOverride = forceOverride)
        val targetExtractDir = NativeExtractor.extractDir

        val strategies = arrayOf(
            LinuxNativeDiscoveryStrategyExtend(
                path = targetExtractDir.absolutePath,
                pluginsPath = "${targetExtractDir.absolutePath}/plugins"
            ),
            MacOsNativeDiscoveryStrategyExtend(
                path = targetExtractDir.absolutePath,
                pluginsPath = "${targetExtractDir.absolutePath}/plugins"
            ),
            WindowsNativeDiscoveryStrategyExtend(
                path = targetExtractDir.absolutePath,
                pluginsPath = "${targetExtractDir.absolutePath}/plugins"
            )
        )

        NativeDiscovery(*strategies)
            .discover()

        logger.info { "NativeDiscovery completed" }
        onReady()
    }
}

private class LinuxNativeDiscoveryStrategyExtend(
    override val path: String,
    override val pluginsPath: String,
) : CustomSearchPathStrategy(path, pluginsPath) {
    override fun supported(): Boolean = RuntimeUtil.isNix()
}

private class WindowsNativeDiscoveryStrategyExtend(
    override val path: String,
    override val pluginsPath: String,
) : CustomSearchPathStrategy(path, pluginsPath) {
    override fun supported(): Boolean = RuntimeUtil.isWindows()
}

private class MacOsNativeDiscoveryStrategyExtend(
    override val path: String,
    override val pluginsPath: String,
) : CustomSearchPathStrategy(path, pluginsPath) {
    override fun supported(): Boolean = RuntimeUtil.isMac()
    override fun onFound(path: String?): Boolean {
        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), path)
        NativeLibrary.getInstance(RuntimeUtil.getLibVlcCoreLibraryName())
        return true
    }
}

private abstract class CustomSearchPathStrategy(
    open val path: String,
    open val pluginsPath: String
) : NativeDiscoveryStrategy {

    override fun discover(): String? = path

    override fun onFound(path: String?): Boolean = true

    override fun onSetPluginPath(path: String?): Boolean {
        return LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", pluginsPath, 1) == 0
    }
}
