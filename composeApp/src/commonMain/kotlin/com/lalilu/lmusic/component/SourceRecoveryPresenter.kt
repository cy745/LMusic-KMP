package com.lalilu.lmusic.component

import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.StartupSourceRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single

/** Application-lifetime notice: navigation or recreating App must not restart its countdown. */
@Single(createdAtStart = true)
class SourceRecoveryPresenter(repository: MediaSourceBindingRepository) {
    private val recovery = StartupSourceRecovery(
        repository.states,
        CoroutineScope(Dispatchers.Default + SupervisorJob()),
    )
    val state = recovery.state
    fun dismiss() = recovery.dismiss()
}
