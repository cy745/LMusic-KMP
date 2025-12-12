package com.lalilu.lmedia

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.lalilu.lmedia.entity.Metadata
import io.github.vinceglb.filekit.*
import kotlinx.io.buffered
import org.scijava.nativelib.NativeLoader

suspend fun main(args: Array<String>) {
    LMediaServer.main(args)
}

object LMediaServer : SuspendingCliktCommand() {
    val port by option(help = "Port to listen on")
        .int()
        .default(7799)

    val host: String by option(help = "Host to listen on")
        .default("0.0.0.0")

    val path: String by argument(help = "Path to file")

    override suspend fun run() {
        echo("Hello World!: $path listen on $host$port")
        loadFile(path)
    }
}


suspend fun SuspendingCliktCommand.loadFile(path: String) {
    val root = PlatformFile(path)

    if (!root.exists()) {
        error("File not found")
    }

    if (!root.isDirectory()) {
        error("Not a directory")
    }

    val files = root.filterChildren { file ->
        if (file.isDirectory()) return@filterChildren false
        if (file.size() < 10) return@filterChildren false

        MagicNumber.match(
            ext = file.extension,
            source = file.source().buffered()
        ) != null
    }

    files.mapNotNull { file ->
        val metadata = TaglibWrapper.readMetadataWithPath(path = file.absolutePath()) ?: return@mapNotNull null
        file to metadata
    }.forEach {
        echo("${it.second.title}: ${it.second.artist}")
    }
}

object TaglibWrapper {
    init {
        runCatching { NativeLoader.loadLibrary("zlib1") }
        NativeLoader.loadLibrary("tag")
    }

    external fun version(): String
    external suspend fun readMetadataWithFD(fd: Int): Metadata?
    external suspend fun readMetadataWithPath(path: String): Metadata?
    external suspend fun getLyricWithFD(fd: Int): String?
    external suspend fun getLyricWithPath(path: String): String?
    external suspend fun getPictureWithFD(fd: Int): ByteArray?
    external suspend fun getPictureWithPath(path: String): ByteArray?
}