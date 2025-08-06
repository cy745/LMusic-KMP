package com.lalilu.lplayer.player

import com.sun.jna.NativeLibrary
import org.scijava.nativelib.BaseJniExtractor
import org.scijava.nativelib.NativeLoader
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.LinuxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.OsxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.WindowsNativeDiscoveryStrategy
import java.io.File

object VLCPlayerLoader {
    private val extractor by lazy { NativeLoader.getJniExtractor() as BaseJniExtractor }
    private val tempDir by lazy { File(extractor.jniDir, "osx").absolutePath }

    private val DEFAULT_SEARCH_STRATEGIES = arrayOf(
        LinuxNativeDiscoveryStrategy(),
        OsxNativeDiscoveryStrategy(),
        WindowsNativeDiscoveryStrategy(),
        LinuxNativeDiscoveryStrategyExtend(pathFunc = { tempDir }, pluginsPathFunc = { "$tempDir/plugins" }),
        MacOsNativeDiscoveryStrategyExtend(pathFunc = { tempDir }, pluginsPathFunc = { "$tempDir/plugins" }),
        WindowsNativeDiscoveryStrategyExtend(pathFunc = { tempDir }, pluginsPathFunc = { "$tempDir/plugins" })
    )

    fun initialize() {
        System.setProperty("vlcj.log", "DEBUG")
        val osxFile = File(extractor.jniDir, "osx").also { if (!it.isDirectory) it.mkdirs() }
        File(osxFile, "plugins").also { if (!it.isDirectory) it.mkdirs() }

        extractor.extractRegistered()
        NativeDiscovery(*DEFAULT_SEARCH_STRATEGIES)
            .discover()
    }
}

private class LinuxNativeDiscoveryStrategyExtend(
    pathFunc: () -> String,
    pluginsPathFunc: () -> String
) : CustomSearchPathStrategy(pathFunc, pluginsPathFunc) {
    override fun supported(): Boolean = RuntimeUtil.isNix()
}

private class WindowsNativeDiscoveryStrategyExtend(
    pathFunc: () -> String,
    pluginsPathFunc: () -> String
) : CustomSearchPathStrategy(pathFunc, pluginsPathFunc) {
    override fun supported(): Boolean = RuntimeUtil.isWindows()
}

private class MacOsNativeDiscoveryStrategyExtend(
    pathFunc: () -> String,
    pluginsPathFunc: () -> String
) : CustomSearchPathStrategy(pathFunc, pluginsPathFunc) {
    override fun supported(): Boolean = RuntimeUtil.isMac()
    override fun onFound(path: String?): Boolean {
        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), path)
        NativeLibrary.getInstance(RuntimeUtil.getLibVlcCoreLibraryName())
        return true
    }
}

private abstract class CustomSearchPathStrategy(
    val pathFunc: () -> String,
    val pluginsPathFunc: () -> String
) : NativeDiscoveryStrategy {

    override fun discover(): String? {
        return pathFunc.invoke()
    }

    override fun onFound(path: String?): Boolean = true

    override fun onSetPluginPath(path: String?): Boolean {
        return LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", pluginsPathFunc(), 1) == 0
    }
}
