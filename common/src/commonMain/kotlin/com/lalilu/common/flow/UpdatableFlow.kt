package com.lalilu.common.flow


import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 将[Flow]转为可手动更新的[UpdatableFlow]
 */
@OptIn(
    ExperimentalCoroutinesApi::class,
    FlowPreview::class, ExperimentalTime::class
)
class UpdatableFlow<T>(
    private val flow: Flow<T>,
    debouncingInterval: Long = 0
) : Flow<T> {
    private val currentTimeFlow = MutableStateFlow(-1L)
    private val rootFlow = currentTimeFlow
        .debounce { value -> if (value == -1L) 0 else debouncingInterval }
        .flatMapLatest { flow }

    override suspend fun collect(collector: FlowCollector<T>) {
        rootFlow.collect(collector)
    }

    fun requireUpdate() {
        currentTimeFlow.tryEmit(Clock.System.now().toEpochMilliseconds())
    }
}

fun <T> Flow<T>.toUpdatableFlow(
    debouncingInterval: Long = 0
): UpdatableFlow<T> {
    return UpdatableFlow(this, debouncingInterval)
}