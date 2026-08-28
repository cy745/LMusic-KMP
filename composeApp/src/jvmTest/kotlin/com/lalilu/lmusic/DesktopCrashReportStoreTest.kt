package com.lalilu.lmusic

import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopCrashReportStoreTest {
    private val temporaryDirectory = Files.createTempDirectory("lmusic-desktop-crash-test-")

    init {
        System.setProperty("lmusic.crashReportDirectory", temporaryDirectory.toString())
    }

    @AfterTest
    fun cleanUp() {
        System.clearProperty("lmusic.crashReportDirectory")
        temporaryDirectory.toFile().deleteRecursively()
    }

    @Test
    fun storesAndPackagesOfficialSentryEnvelope() {
        val options = SentryOptions().apply {
            release = "com.lalilu.lmusic@test"
            environment = "offline-feedback-desktop"
        }
        val event = SentryEvent().apply {
            level = SentryLevel.FATAL
            exceptions = listOf(SentryException().apply {
                type = IllegalStateException::class.java.name
                value = "Desktop crash test"
                mechanism = Mechanism().apply {
                    type = "UncaughtExceptionHandler"
                    isHandled = false
                }
            })
        }
        val envelope = SentryEnvelope.from(options.serializer, event, null)

        val reportId = assertNotNull(
            DesktopCrashReportStore.storeSentryCrash(options, envelope, event),
        )
        val reportDirectory = assertNotNull(DesktopCrashReportStore.reportDirectoryFor(reportId))

        assertTrue(reportDirectory.resolve("event.envelope").isRegularFile())
        assertTrue(reportDirectory.resolve("event.json").isRegularFile())
        assertTrue(reportDirectory.resolve("logs.txt").isRegularFile())
        assertTrue(reportDirectory.resolve("README.txt").isRegularFile())
        assertTrue(reportDirectory.resolve(".complete").isRegularFile())
        assertTrue(reportDirectory.resolve("report.txt").readText().contains("Desktop crash test"))
        assertEquals(reportId, DesktopCrashReportStore.latestReportId(onlyUnviewed = true))

        val archive = assertNotNull(DesktopCrashReportStore.createFeedbackArchive(reportId))
        ZipFile(archive.toFile()).use { zip ->
            assertEquals(
                setOf("README.txt", "event.envelope", "event.json", "report.txt", "logs.txt"),
                zip.entries().asSequence().map { it.name }.toSet(),
            )
        }

        DesktopCrashReportStore.markViewed(reportId)
        assertFalse(DesktopCrashReportStore.latestReportId(onlyUnviewed = true) == reportId)
        assertEquals(reportId, DesktopCrashReportStore.latestReportId(onlyUnviewed = false))
    }

    @Test
    fun rejectsInvalidReportIds() {
        assertEquals(null, DesktopCrashReportStore.readReport("../outside"))
        assertEquals(null, DesktopCrashReportStore.reportDirectoryFor("not-an-event-id"))
    }
}

class DesktopCrashReporterRequestTest {
    @Test
    fun parsesInlineReportId() {
        val id = "0123456789abcdef0123456789abcdef"
        assertEquals(id, DesktopCrashReporter.parseRequest(arrayOf("--crash-report=$id"))?.reportId)
    }

    @Test
    fun parsesLatestAndIgnoresNormalLaunch() {
        assertEquals(null, DesktopCrashReporter.parseRequest(arrayOf("--crash-report=latest"))?.reportId)
        assertEquals(null, DesktopCrashReporter.parseRequest(emptyArray()))
    }
}
