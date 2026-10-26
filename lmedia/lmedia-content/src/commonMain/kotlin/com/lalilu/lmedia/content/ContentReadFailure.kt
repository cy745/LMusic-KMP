package com.lalilu.lmedia.content

import kotlinx.io.IOException

/** Transport facts only; queue navigation and user intent still belong to PlaybackCore. */
enum class ContentFailureKind { Authentication, Missing, Changed, InvalidResponse, Temporary }

class ContentReadFailure(
    val kind: ContentFailureKind,
    val statusCode: Int? = null,
    message: String,
) : IOException(message) {
    val retryable: Boolean get() = kind == ContentFailureKind.Temporary
}

/** Platform loaders may wrap the original transport exception. */
fun Throwable.contentReadFailure(): ContentReadFailure? {
    var current: Throwable? = this
    repeat(20) {
        if (current is ContentReadFailure) return current as ContentReadFailure
        current = current?.cause ?: return null
    }
    return null
}

internal fun requireValidContent(valid: Boolean, message: String) {
    if (!valid) throw ContentReadFailure(ContentFailureKind.InvalidResponse, message = message)
}
