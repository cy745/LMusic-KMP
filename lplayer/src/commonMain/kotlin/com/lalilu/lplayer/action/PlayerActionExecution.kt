package com.lalilu.lplayer.action

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Fire-and-forget UI/system callback boundary; suspend APIs must retain their failure result. */
internal fun CoroutineScope.launchPlayerAction(
    onFailure: (Exception) -> Unit = { Logger.e(it) { "Playback action failed" } },
    action: suspend () -> Unit,
): Job = launch {
    try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onFailure(failure)
    }
}
