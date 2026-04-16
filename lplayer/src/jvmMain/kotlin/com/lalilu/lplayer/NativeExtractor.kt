package com.lalilu.lplayer

import com.lalilu.common.ext.io
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.*

object NativeExtractor {
    const val TAG = "NativeExtractor"

    private val logger = KotlinLogging.logger(TAG)
    private val classLoader by lazy { Thread.currentThread().contextClassLoader }
    private val osName: String by lazy { System.getProperty("os.name").lowercase(Locale.getDefault()) }
    val extractDir by lazy { File(FileKit.filesDir.file, "native_libs") }

    init {
        val nativeLibPath = extractDir.absolutePath
        System.setProperty("jna.library.path", nativeLibPath)
    }

    private val platformDir by lazy {
        when {
            isMac() -> "osx"
            isWin() -> "win"
            else -> "linux"
        }
    }

    suspend fun readExtractList(): List<String> = withContext(Dispatchers.io) {
        val ins = classLoader.getResourceAsStream("${platformDir}/AUTOEXTRACT.LIST")
            ?: return@withContext emptyList()
        val reader = InputStreamReader(ins)
        reader.readLines()
    }

    suspend fun doExtract(forceOverride: Boolean = false) = withContext(Dispatchers.io) {
        logger.info { "Start doExtract, targetExtractDir: ${extractDir.absolutePath}" }
        val extractList = readExtractList()
        if (extractList.isEmpty()) {
            throw IllegalStateException("extractList is empty")
        }

        extractList.forEachIndexed { index, str ->
            debugLog { "[${index + 1}/${extractList.size}]: $str" }
        }

        val jobs = extractList.mapIndexedNotNull { index, str ->
            val targetFile = File(extractDir, str.removePrefix(platformDir))
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
        logger.info { "Extract completed" }
    }

    fun isNix() = listOf("nux", "nix", "freebsd").any { osName.contains(it) }
    fun isMac() = listOf("mac", "darwin").any { osName.contains(it) }
    fun isWin() = listOf("win").any { osName.contains(it) }


    var showDebugLog: Boolean = false
    private fun debugLog(msg: () -> String) {
        if (showDebugLog) {
            logger.debug(msg)
        }
    }
}