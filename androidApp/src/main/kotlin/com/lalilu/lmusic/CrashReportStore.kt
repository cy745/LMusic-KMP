package com.lalilu.lmusic

import android.app.Activity
import android.content.Context
import com.lalilu.lmusic.util.MemoryLogWriter
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Stores official Sentry Envelopes locally until the user explicitly shares them. */
object CrashReportStore {
    private const val REPORT_ROOT = "crash-reports"
    private const val SHARE_ROOT = "crash-report-share"
    private const val COMPLETE_MARKER = ".complete"
    private const val VIEWED_MARKER = ".viewed"
    private const val MAX_REPORTS = 8
    private const val MAX_LOG_ITEMS = 500

    fun showPendingReport(activity: Activity): Boolean {
        val reportId = latestReport(activity, onlyUnviewed = true)?.name ?: return false
        activity.startActivity(CrashReportActivity.intent(activity, reportId))
        return true
    }

    /** Returns the newest complete report, including reports the user has already viewed. */
    fun latestReportId(context: Context): String? =
        latestReport(context, onlyUnviewed = false)?.name

    fun markViewed(context: Context, reportId: String) {
        reportDirectory(context, reportId)
            ?.resolve(VIEWED_MARKER)
            ?.runCatching { writeText("") }
    }

    fun readReport(context: Context, reportId: String): String? =
        reportDirectory(context, reportId)
            ?.resolve("report.txt")
            ?.takeIf(File::isFile)
            ?.runCatching(File::readText)
            ?.getOrNull()

    fun createFeedbackArchive(context: Context, reportId: String): File? {
        val reportDir = reportDirectory(context, reportId) ?: return null
        if (!reportDir.resolve(COMPLETE_MARKER).isFile) return null

        val shareDir = File(context.cacheDir, SHARE_ROOT).apply { mkdirs() }
        val output = File(shareDir, "LMusic-crash-$reportId.zip")
        val includedFiles = listOf(
            "README.txt",
            "event.envelope",
            "event.json",
            "report.txt",
            "logs.txt",
        )

        return runCatching {
            ZipOutputStream(FileOutputStream(output)).use { zip ->
                includedFiles.forEach { name ->
                    val file = reportDir.resolve(name)
                    if (!file.isFile) return@forEach
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            output
        }.getOrNull()
    }

    @Synchronized
    internal fun storeSentryCrash(
        context: Context,
        options: SentryOptions,
        envelope: SentryEnvelope,
        event: SentryEvent,
    ): String? = runCatching {
        val reportId = envelope.header.eventId?.toString()
            ?.takeIf { it.matches(Regex("[a-fA-F0-9]{32}")) }
            ?: UUID.randomUUID().toString().replace("-", "")
        val reportDir = File(File(context.filesDir, REPORT_ROOT), reportId).apply { mkdirs() }
        val eventJson = StringWriter().also { writer ->
            options.serializer.serialize(event, writer)
        }.toString()

        reportDir.resolve("event.envelope").outputStream().use { output ->
            options.serializer.serialize(envelope, output)
        }
        reportDir.resolve("event.json").writeText(eventJson)
        reportDir.resolve("report.txt").writeText(createReadableReport(reportId, eventJson))
        reportDir.resolve("logs.txt").writeText(snapshotLogs())
        reportDir.resolve("README.txt").writeText(
            """
            This feedback archive was captured and serialized locally by the official Sentry Android SDK.
            It was not uploaded automatically.

            event.envelope  Original Sentry Envelope, including any Sentry attachments
            event.json      Sentry event payload
            report.txt      Human-readable summary and formatted Sentry event
            logs.txt        Recent LMusic in-memory logs

            Logs and stack traces can contain local file paths or media names. The user explicitly
            chose to export this archive through Android's system share sheet.
            """.trimIndent()
        )
        reportDir.resolve(COMPLETE_MARKER).writeText("")
        purgeOldReports(context)
        reportId
    }.getOrNull()

    private fun createReadableReport(reportId: String, eventJson: String): String {
        val event = runCatching { JSONObject(eventJson) }.getOrNull()
        val exceptionValues = event
            ?.optJSONObject("exception")
            ?.optJSONArray("values")
        val exception = exceptionValues
            ?.takeIf { it.length() > 0 }
            ?.optJSONObject(exceptionValues.length() - 1)
        val formattedEvent = event?.toString(2) ?: eventJson

        return buildString {
            appendLine("LMusic offline Sentry crash report")
            appendLine("Event ID: $reportId")
            appendLine("Level: ${event?.optString("level")?.ifBlank { "unknown" } ?: "unknown"}")
            appendLine("Platform: ${event?.optString("platform")?.ifBlank { "unknown" } ?: "unknown"}")
            appendLine("Release: ${event?.optString("release")?.ifBlank { "unknown" } ?: "unknown"}")
            if (exception != null) {
                appendLine("Exception: ${exception.optString("type")} — ${exception.optString("value")}")
            }
            appendLine()
            append(formattedEvent)
        }
    }

    private fun snapshotLogs(): String = runCatching {
        MemoryLogWriter.logs.takeLast(MAX_LOG_ITEMS).joinToString(separator = "\n") { item ->
            buildString {
                append(item.toString())
                item.throwable?.let { throwable ->
                    appendLine()
                    append(throwable.stackTraceToString())
                }
            }
        }.ifBlank { "No in-memory logs were available before the crash." }
    }.getOrDefault("Unable to read the in-memory log buffer after the crash.")

    private fun latestReport(context: Context, onlyUnviewed: Boolean): File? =
        File(context.filesDir, REPORT_ROOT)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .filter { it.resolve(COMPLETE_MARKER).isFile }
            .filter { !onlyUnviewed || !it.resolve(VIEWED_MARKER).exists() }
            .maxByOrNull { it.resolve(COMPLETE_MARKER).lastModified() }

    private fun reportDirectory(context: Context, reportId: String): File? {
        if (!reportId.matches(Regex("[a-fA-F0-9]{32}"))) return null
        return File(File(context.filesDir, REPORT_ROOT), reportId)
            .takeIf(File::isDirectory)
    }

    private fun purgeOldReports(context: Context) {
        File(context.filesDir, REPORT_ROOT)
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.resolve(COMPLETE_MARKER).isFile }
            .sortedByDescending { it.resolve(COMPLETE_MARKER).lastModified() }
            .drop(MAX_REPORTS)
            .forEach { it.deleteRecursively() }
    }
}
