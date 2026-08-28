package com.lalilu.lmusic

import io.sentry.Hint
import io.sentry.ITransportFactory
import io.sentry.RequestDetails
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.Enqueable
import io.sentry.hints.Retryable
import io.sentry.hints.SubmissionResult
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import io.sentry.util.HintUtils
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** Uses the official Sentry Java SDK while keeping every event on the user's computer. */
internal object DesktopOfflineSentryReporter {
    private const val OFFLINE_DSN = "https://offline@offline.invalid/1"
    private val installed = AtomicBoolean(false)

    fun install() {
        if (!installed.compareAndSet(false, true)) return

        Sentry.init { options ->
            options.dsn = OFFLINE_DSN
            options.environment = "offline-feedback-desktop"
            options.release = "com.lalilu.lmusic@1.0.0"
            options.setTransportFactory(DesktopLocalTransportFactory())
            options.isSendDefaultPii = false
            options.isEnableUncaughtExceptionHandler = true
            options.isEnableAutoSessionTracking = false
            options.isSendClientReports = false
            options.tracesSampleRate = 0.0
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                event.user = null
                event.serverName = null
                event.contexts.device?.id = null
                event
            }
        }
    }
}

private class DesktopLocalTransportFactory : ITransportFactory {
    override fun create(
        options: SentryOptions,
        requestDetails: RequestDetails,
    ): ITransport = DesktopLocalTransport(options)
}

private class DesktopLocalTransport(
    private val options: SentryOptions,
) : ITransport {
    private val reporterStarted = AtomicBoolean(false)

    override fun send(envelope: SentryEnvelope, hint: Hint) {
        var stored = false
        try {
            val event = envelope.items
                .asSequence()
                .mapNotNull { item ->
                    runCatching { item.getEvent(options.serializer) }.getOrNull()
                }
                .firstOrNull()

            if (event != null && event.isCrashEvent()) {
                val reportId = DesktopCrashReportStore.storeSentryCrash(
                    options = options,
                    envelope = envelope,
                    event = event,
                )
                stored = reportId != null
                if (reportId != null && reporterStarted.compareAndSet(false, true)) {
                    DesktopAppLauncher.launchCrashReporter(reportId)
                }
            } else {
                // Sessions, tracing and manually captured non-fatal events are intentionally dropped.
                stored = true
            }
        } finally {
            notifySentry(hint, envelope, stored)
        }
    }

    override fun flush(timeoutMillis: Long) = Unit

    override fun getRateLimiter(): RateLimiter? = null

    override fun close() = Unit

    override fun close(isRestarting: Boolean) = Unit

    private fun SentryEvent.isCrashEvent(): Boolean = isCrashed || level == SentryLevel.FATAL

    private fun notifySentry(
        hint: Hint,
        envelope: SentryEnvelope,
        success: Boolean,
    ) {
        val sdkHint = HintUtils.getSentrySdkHint(hint)
        (sdkHint as? Enqueable)?.markEnqueued()
        (sdkHint as? SubmissionResult)?.setResult(success)
        (sdkHint as? Retryable)?.setRetry(!success)

        if (
            success &&
            sdkHint is DiskFlushNotification &&
            sdkHint.isFlushable(envelope.header.eventId)
        ) {
            sdkHint.markFlushed()
        }
    }
}

internal object DesktopAppLauncher {
    fun launchCrashReporter(reportId: String): Boolean = launch("--crash-report=$reportId")

    fun launchMainApplication(): Boolean = launch()

    private fun launch(vararg applicationArguments: String): Boolean = runCatching {
        val processInfo = ProcessHandle.current().info()
        val executable = processInfo.command().orElseThrow()
        val command = if (isJavaLauncher(executable)) {
            javaLaunchCommand(executable)
        } else {
            listOf(executable)
        }

        ProcessBuilder(command + applicationArguments)
            .directory(Path.of(System.getProperty("user.dir")).toFile())
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        true
    }.getOrDefault(false)

    private fun isJavaLauncher(executable: String): Boolean {
        val name = Path.of(executable).fileName.toString().lowercase().removeSuffix(".exe")
        return name == "java" || name == "javaw"
    }

    private fun javaLaunchCommand(executable: String): List<String> {
        val mainCommand = System.getProperty("sun.java.command")
            ?.substringBefore(' ')
            ?.takeIf(String::isNotBlank)
            ?: "com.lalilu.lmusic.MainKt"
        val safeJvmArguments = ManagementFactory.getRuntimeMXBean().inputArguments
            .filterNot { argument ->
                argument.startsWith("-agentlib:jdwp") ||
                    argument.startsWith("-javaagent:") ||
                    argument.startsWith("-agentpath:")
            }

        return buildList {
            add(executable)
            addAll(safeJvmArguments)
            if (mainCommand.endsWith(".jar", ignoreCase = true)) {
                add("-jar")
                add(mainCommand)
            } else {
                add("-cp")
                add(System.getProperty("java.class.path"))
                add(mainCommand)
            }
        }
    }
}
