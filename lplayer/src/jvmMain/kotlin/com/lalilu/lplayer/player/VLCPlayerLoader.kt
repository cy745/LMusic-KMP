package com.lalilu.lplayer.player

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.lalilu.common.ext.ReadyState
import com.lalilu.common.ext.io
import com.lalilu.common.ext.readyStateImpl
import com.sun.jna.NativeLibrary
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import kotlinx.coroutines.*
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import kotlin.coroutines.CoroutineContext

object VLCPlayerLoader : CoroutineScope, ReadyState by readyStateImpl() {
    override val coroutineContext: CoroutineContext = Dispatchers.io

    const val TAG = "VLCPlayerLoader"

    private val logger = Logger(tag = TAG, config = StaticConfig())
    private inline fun debugLog(crossinline msg: () -> String) {
        if (showDebugLog) {
            logger.d(tag = TAG, message = msg)
        }
    }

    var showDebugLog: Boolean = false
    private val classLoader = VLCPlayerLoader::class.java.classLoader
    private val targetExtractDir by lazy { File(FileKit.filesDir.file, "libvlc") }
    private val platformDir by lazy {
        when {
            RuntimeUtil.isMac() -> "osx"
            RuntimeUtil.isWindows() -> "win"
            else -> "linux"
        }
    }

    init {
        System.setProperty("vlcj.log", "DEBUG")
    }

    fun initialize(forceOverride: Boolean = false) = launch {
        Logger.i(tag = TAG, messageString = "start Initialize")

        if (NativeDiscovery().discover()) {
            Logger.i(tag = TAG, messageString = "Native library found, Skip Extract")
            onReady()
            return@launch
        }

        Logger.i(tag = TAG, messageString = "Start Initialize, targetExtractDir: ${targetExtractDir.absolutePath}")
        val extractList = readExtractList()
        if (extractList.isEmpty()) {
            throw IllegalStateException("extractList is empty")
        }

        extractList.forEachIndexed { index, str ->
            debugLog { "[${index + 1}/${extractList.size}]: $str" }
        }

        val jobs = extractList.mapIndexedNotNull { index, str ->
            val targetFile = File(targetExtractDir, str.removePrefix(platformDir))
            if (targetFile.exists() && !forceOverride) {
                debugLog { "[${index + 1}/${extractList.size}] File exists: ${targetFile.absolutePath}" }
                return@mapIndexedNotNull null
            }

            if (targetFile.parentFile?.exists() != true) {
                targetFile.parentFile?.mkdirs()
            }

            if (!targetFile.exists()) {
                targetFile.createNewFile()
            }

            val ins = classLoader.getResourceAsStream(str)

            if (ins == null) {
                debugLog { "[${index + 1}/${extractList.size}] Not found $str" }
                return@mapIndexedNotNull null
            }

            async {
                val out = FileOutputStream(targetFile)
                ins.use { out.use { ins.copyTo(out) } }
                debugLog { "[${index + 1}/${extractList.size}] Extracted ${targetFile.absolutePath}" }
            }
        }

        jobs.awaitAll()
        Logger.i("Extract completed")

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

        Logger.i("NativeDiscovery completed")
        onReady()
    }

    private fun readExtractList(): List<String> {
        val ins = classLoader.getResourceAsStream("${platformDir}/AUTOEXTRACT.LIST")
            ?: return emptyList()
        val reader = InputStreamReader(ins)
        return reader.readLines()
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
