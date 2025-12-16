package com.lalilu.lmedia

import KvSettingsSaver
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.lalilu.common.ext.KModule
import com.lalilu.common.kv.KVSaver
import com.russhwolf.settings.Settings
import dev.whyoleg.sweetspi.ServiceLoader
import io.github.vinceglb.filekit.*
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.bind
import org.koin.dsl.module

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
        .default("/Users/miku/Music/test")

    override suspend fun run() {
        echo("Hello World!: $path listen on $host$port")
        val settings = Settings()

        startKoin {
            printLogger(Level.DEBUG)
            modules(module {
                single<Json> { Json { ignoreUnknownKeys = true } }
                single { KvSettingsSaver(settings) } bind KVSaver::class
            })
            modules(
                ServiceLoader.load(KModule::class)
                    .map(KModule::get)
            )
        }

        loadFile(path)
    }
}


suspend fun SuspendingCliktCommand.loadFile(path: String) {
    val root = PlatformFile(path)

    if (!root.exists()) {
        error("File not found: ${root.absolutePath()}")
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