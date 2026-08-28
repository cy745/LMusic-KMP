package com.lalilu.lmusic

import com.lalilu.lmusic.util.MemoryLogWriter
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Stores official Sentry Java envelopes locally until the user explicitly exports a ZIP. */
internal object DesktopCrashReportStore {
    private const val COMPLETE_MARKER = ".complete"
    private const val VIEWED_MARKER = ".viewed"
    private const val MAX_REPORTS = 8
    private const val MAX_LOG_ITEMS = 500
    private const val REPORT_DIRECTORY_PROPERTY = "lmusic.crashReportDirectory"
    private val reportIdPattern = Regex("[a-fA-F0-9]{32}")

    val rootDirectory: Path
        get() = System.getProperty(REPORT_DIRECTORY_PROPERTY)
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: defaultAppDataDirectory().resolve("crash-reports")

    fun latestReportId(onlyUnviewed: Boolean): String? = latestReport(onlyUnviewed)?.name

    fun readReport(reportId: String): String? = reportDirectory(reportId)
        ?.resolve("report.txt")
        ?.takeIf(Path::isRegularFile)
        ?.runCatching(Path::readText)
        ?.getOrNull()

    fun markViewed(reportId: String) {
        reportDirectory(reportId)
            ?.resolve(VIEWED_MARKER)
            ?.runCatching { writeText("") }
    }

    fun reportDirectoryFor(reportId: String): Path? = reportDirectory(reportId)

    fun createFeedbackArchive(reportId: String): Path? {
        val reportDirectory = reportDirectory(reportId) ?: return null
        if (!reportDirectory.resolve(COMPLETE_MARKER).isRegularFile()) return null

        val archiveDirectory = rootDirectory.resolve("exports").createDirectories()
        val output = archiveDirectory.resolve("LMusic-crash-$reportId.zip")
        val includedFiles = listOf(
            "README.txt",
            "event.envelope",
            "event.json",
            "report.txt",
            "logs.txt",
        )

        return runCatching {
            ZipOutputStream(output.outputStream()).use { zip ->
                includedFiles.forEach { name ->
                    val file = reportDirectory.resolve(name)
                    if (!file.isRegularFile()) return@forEach
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            output
        }.getOrNull()
    }

    @Synchronized
    fun storeSentryCrash(
        options: SentryOptions,
        envelope: SentryEnvelope,
        event: SentryEvent,
    ): String? = runCatching {
        val reportId = envelope.header.eventId?.toString()
            ?.takeIf(reportIdPattern::matches)
            ?: UUID.randomUUID().toString().replace("-", "")
        val reportDirectory = rootDirectory.resolve(reportId).createDirectories()
        val eventJson = StringWriter().also { writer ->
            options.serializer.serialize(event, writer)
        }.toString()

        reportDirectory.resolve("event.envelope").outputStream().use { output ->
            options.serializer.serialize(envelope, output)
        }
        reportDirectory.resolve("event.json").writeText(eventJson)
        reportDirectory.resolve("report.txt").writeText(createReadableReport(reportId, eventJson))
        reportDirectory.resolve("logs.txt").writeText(snapshotLogs())
        reportDirectory.resolve("README.txt").writeText(
            """
            This feedback archive was captured and serialized locally by the official Sentry Java SDK.
            It was not uploaded automatically.

            event.envelope  Original Sentry Envelope, including any Sentry attachments
            event.json      Sentry event payload
            report.txt      Human-readable summary and formatted Sentry event
            logs.txt        Recent LMusic in-memory logs

            Logs and stack traces can contain local file paths or media names. The user explicitly
            chose to export this archive from the LMusic desktop crash reporter.
            """.trimIndent()
        )
        reportDirectory.resolve(COMPLETE_MARKER).writeText("")
        purgeOldReports()
        reportId
    }.getOrNull()

    private fun createReadableReport(reportId: String, eventJson: String): String {
        val event = runCatching { Json.parseToJsonElement(eventJson).jsonObject }.getOrNull()
        val exception = runCatching {
            event?.get("exception")
                ?.jsonObject
                ?.get("values")
                ?.jsonArray
                ?.lastOrNull()
                ?.jsonObject
        }.getOrNull()
        val formattedEvent = runCatching {
            Json { prettyPrint = true }.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                Json.parseToJsonElement(eventJson),
            )
        }.getOrDefault(eventJson)

        fun value(name: String): String = event
            ?.get(name)
            ?.jsonPrimitive
            ?.contentOrNull
            ?.ifBlank { "unknown" }
            ?: "unknown"

        return buildString {
            appendLine("LMusic offline Sentry crash report")
            appendLine("Event ID: $reportId")
            appendLine("Level: ${value("level")}")
            appendLine("Platform: ${value("platform")}")
            appendLine("Release: ${value("release")}")
            if (exception != null) {
                val type = exception["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val message = exception["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
                appendLine("Exception: $type — $message")
            }
            appendLine()
            append(formattedEvent)
        }
    }

    private fun snapshotLogs(): String = runCatching {
        MemoryLogWriter.logs.toList().takeLast(MAX_LOG_ITEMS).joinToString(separator = "\n") { item ->
            buildString {
                append(item.toString())
                item.throwable?.let { throwable ->
                    appendLine()
                    append(throwable.stackTraceToString())
                }
            }
        }.ifBlank { "No in-memory logs were available before the crash." }
    }.getOrDefault("Unable to read the in-memory log buffer after the crash.")

    private fun latestReport(onlyUnviewed: Boolean): Path? = rootDirectory
        .takeIf(Path::isDirectory)
        ?.runCatching(Path::listDirectoryEntries)
        ?.getOrDefault(emptyList())
        ?.asSequence()
        ?.filter(Path::isDirectory)
        ?.filter { it.resolve(COMPLETE_MARKER).isRegularFile() }
        ?.filter { !onlyUnviewed || !it.resolve(VIEWED_MARKER).exists() }
        ?.maxByOrNull { Files.getLastModifiedTime(it.resolve(COMPLETE_MARKER)).toMillis() }

    private fun reportDirectory(reportId: String): Path? {
        if (!reportIdPattern.matches(reportId)) return null
        return rootDirectory.resolve(reportId).takeIf(Path::isDirectory)
    }

    private fun purgeOldReports() {
        rootDirectory
            .takeIf(Path::isDirectory)
            ?.runCatching(Path::listDirectoryEntries)
            ?.getOrDefault(emptyList())
            .orEmpty()
            .filter { it.isDirectory() && it.resolve(COMPLETE_MARKER).isRegularFile() }
            .sortedByDescending { Files.getLastModifiedTime(it.resolve(COMPLETE_MARKER)).toMillis() }
            .drop(MAX_REPORTS)
            .forEach(::deleteDirectory)
    }

    private fun deleteDirectory(directory: Path) {
        if (!directory.startsWith(rootDirectory) || !directory.isDirectory()) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Path::deleteIfExists)
        }
    }

    private fun defaultAppDataDirectory(): Path {
        val userHome = Path.of(System.getProperty("user.home"))
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") -> userHome.resolve("Library/Application Support/LMusic")
            osName.contains("win") -> System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.resolve("LMusic")
                ?: userHome.resolve("AppData/Local/LMusic")
            else -> System.getenv("XDG_DATA_HOME")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.resolve("LMusic")
                ?: userHome.resolve(".local/share/LMusic")
        }
    }

    internal fun copyArchive(archive: Path, destination: Path): Path =
        Files.copy(archive, destination, StandardCopyOption.REPLACE_EXISTING)
}
