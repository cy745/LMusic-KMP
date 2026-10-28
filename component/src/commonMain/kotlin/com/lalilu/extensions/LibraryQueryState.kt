package com.lalilu.extensions

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** A query's lifecycle is separate from its last successful value (including an empty result). */
class LibraryQueryState<T>(initial: T) : State<T> {
    private data class Snapshot<T>(val value: T, val loading: Boolean = true,
        val hasResult: Boolean = false, val error: Throwable? = null)
    private var snapshot by mutableStateOf(Snapshot(initial))
    override val value: T get() = snapshot.value
    val loading get() = snapshot.loading
    val hasResult get() = snapshot.hasResult
    val error get() = snapshot.error
    internal val retries = MutableStateFlow(0)
    fun retry() { retries.value += 1 }
    internal fun begin() { snapshot = snapshot.copy(loading = true, error = null) }
    internal fun accept(value: T) { snapshot = Snapshot(value, loading = false, hasResult = true) }
    internal fun fail(error: Throwable) { snapshot = snapshot.copy(loading = false, error = error) }
}

/** Each query owns its sorting/filtering and cancellation; an old query cannot publish a late result. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <Q, T> Flow<Q>.queryState(
    initial: T,
    scope: CoroutineScope,
    query: (Q) -> Flow<T>,
): LibraryQueryState<T> = LibraryQueryState(initial).also { state ->
    state.retries.flatMapLatest {
        this@queryState.flatMapLatest { key ->
            // Include query construction in the error boundary as well as collection.
            flow { emitAll(query(key)) }
                .onStart { state.begin() }
                .onEach { state.accept(it) }
                .catch { state.fail(it) }
        }.onStart { state.begin() }
            // Query keys can themselves come from a repository (e.g. playlist membership).
            // Retrying must resubscribe to that upstream after it has failed.
            .catch { state.fail(it) }
    }.launchIn(scope)
}

fun <T> Flow<T>.toQueryState(initial: T, scope: CoroutineScope): LibraryQueryState<T> =
    flowOf(Unit).queryState(initial, scope) { this }
