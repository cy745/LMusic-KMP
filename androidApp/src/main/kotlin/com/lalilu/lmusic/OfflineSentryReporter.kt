package com.lalilu.lmusic

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import io.sentry.Hint
import io.sentry.ITransportFactory
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.Enqueable
import io.sentry.hints.Retryable
import io.sentry.hints.SubmissionResult
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import io.sentry.util.HintUtils
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uses the official Sentry Android SDK for capture and serialization, but replaces its network
 * transport with a local report store. Nothing leaves the device until the user shares a ZIP.
 */
object OfflineSentryReporter {
    // A syntactically valid DSN is required to enable the SDK. The reserved .invalid domain can
    // never receive data, and our local transport replaces Sentry's HTTP transport entirely.
    private const val OFFLINE_DSN = "https://offline@offline.invalid/1"
    private val installed = AtomicBoolean(false)

    fun install(application: Application) {
        if (!isMainProcess(application) || !installed.compareAndSet(false, true)) return

        SentryAndroid.init(application) { options ->
            options.dsn = OFFLINE_DSN
            options.environment = "offline-feedback"
            options.setTransportFactory(LocalTransportFactory(application))
            options.isSendDefaultPii = false
            options.isEnableUncaughtExceptionHandler = true
            options.isAnrEnabled = true
            options.isEnableNdk = true
            options.isEnableAutoSessionTracking = false
            options.isEnableNetworkEventBreadcrumbs = false
            options.isSendClientReports = false
            options.tracesSampleRate = 0.0
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                // A stable installation identifier is unnecessary for user-initiated, offline
                // feedback and would allow separate archives to be correlated.
                event.user = null
                event.contexts.device?.id = null
                event
            }
        }
    }

    private fun isMainProcess(context: Context): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            manager?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
                ?: runCatching {
                    File("/proc/self/cmdline").readText().substringBefore('\u0000')
                }.getOrNull()
        }
        return processName == context.packageName
    }
}

private class LocalTransportFactory(
    private val context: Context,
) : ITransportFactory {
    override fun create(
        options: SentryOptions,
        requestDetails: RequestDetails,
    ): ITransport = LocalSentryTransport(context, options)
}

private class LocalSentryTransport(
    private val context: Context,
    private val options: SentryOptions,
) : ITransport {
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
                val reportId = CrashReportStore.storeSentryCrash(
                    context = context,
                    options = options,
                    envelope = envelope,
                    event = event,
                )
                stored = reportId != null
                if (reportId != null) {
                    runCatching {
                        context.startActivity(CrashReportActivity.intent(context, reportId))
                    }
                }
            } else {
                // Non-crash Sentry events (sessions, app lifecycle, tracing) are intentionally
                // discarded. Marking them handled keeps the SDK outbox from retrying forever.
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
