package com.lalilu.lmedia

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.lalilu.lmedia.server.LMediaServer
import com.lalilu.lmedia.server.entity.RemoteServerConfig
import com.lalilu.lmedia.source.JvmFileSystemSource
import kotlinx.serialization.json.Json

suspend fun main(args: Array<String>) {
    LMediaServerCommand.main(args)
}

object LMediaServerCommand : SuspendingCliktCommand() {
    val port by option(help = "Port to listen on")
        .int()
        .default(7799)

    val path: String by argument(help = "Path to file")
        .default("/Users/miku/Music/test")

    override suspend fun run() {
        echo("Hello World!: $path listen on $port")

        val json = Json { ignoreUnknownKeys = true }
        val jvmSource = JvmFileSystemSource()
        jvmSource.config.update { setter -> setter("file_path", path) }

        val server = LMediaServer(
            config = RemoteServerConfig(
                port = port,
                sourceName = jvmSource.name
            ),
            sources = PlatformMediaSource(listOf(jvmSource)),
            json = json
        )

        server.startSync()
    }
}