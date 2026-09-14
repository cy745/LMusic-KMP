package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.source.MediaContentAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceRecoveryNotice(
    val pendingSources: Set<String>,
    val remainingSeconds: Int = 15,
    val dismissed: Boolean = false,
) {
    val visible: Boolean get() = pendingSources.isNotEmpty() && remainingSeconds > 0 && !dismissed
}

/** One notice per process startup. Hiding it never cancels source work or changes playback. */
class StartupSourceRecovery(
    statuses: StateFlow<Map<String, SourceStatus>>,
    scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(SourceRecoveryNotice(
        pendingSources = statuses.value.filterValues { it.isWaitingForStartup() }.keys.toSet(),
    ))
    val state: StateFlow<SourceRecoveryNotice> = mutableState.asStateFlow()

    init {
        scope.launch {
            statuses.collect { current ->
                mutableState.update { notice ->
                    notice.copy(pendingSources = notice.pendingSources.filterTo(mutableSetOf()) {
                        current[it]?.isWaitingForStartup() == true
                    })
                }
            }
        }
        scope.launch {
            while (mutableState.value.visible) {
                delay(1_000)
                mutableState.update { it.copy(remainingSeconds = (it.remainingSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun dismiss() { mutableState.update { it.copy(dismissed = true) } }
}

private fun SourceStatus.isWaitingForStartup(): Boolean {
    if (!enabled || enablementError != null) return false
    if (enablementChanging) return true
    return when (contentAvailability) {
        MediaContentAvailability.Uninitialized, MediaContentAvailability.Preparing -> true
        is MediaContentAvailability.Unavailable -> false
        MediaContentAvailability.Ready -> {
            // The source may be readable before its first database update has finished.
            val revision = when (val commit = commitState) {
                is SnapshotCommitState.Committed -> commit.revision
                is SnapshotCommitState.Failed -> commit.revision
                else -> null
            }
            revision == null || revision != resultRevision
        }
    }
}
