package com.lalilu.extensions

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf

class LazyListRecordScope internal constructor(
    var recorder: ItemRecorder,
) : LazyListScope {
    var lazyListScope: LazyListScope? = null
        internal set

    override fun item(key: Any?, contentType: Any?, content: @Composable (LazyItemScope.() -> Unit)) {
        lazyListScope?.let { scope ->
            recorder.record(key)
            scope.item(
                key = key,
                contentType = contentType,
                content = content
            )
        }
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable (LazyItemScope.(index: Int) -> Unit)
    ) {
        lazyListScope?.let { scope ->
            recorder.recordAll((0 until count).map { key?.invoke(it) })
            scope.items(
                count = count,
                key = key,
                contentType = contentType,
                itemContent = itemContent
            )
        }
    }

    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable (LazyItemScope.(Int) -> Unit)) {
        lazyListScope?.let { scope ->
            recorder.record(key)
            scope.stickyHeader(
                key = key,
                contentType = contentType,
                content = content
            )
        }
    }
}

class ItemRecorder {
    private val keys = mutableStateListOf<Any?>()
    private val scope = LazyListRecordScope(this)

    fun record(key: Any?) = this.keys.add(key)
    fun recordAll(keys: List<Any?>) = this.keys.addAll(keys)
    fun clear() = keys.clear()
    fun list() = keys

    internal fun startRecord(
        lazyListScope: LazyListScope,
        block: LazyListRecordScope.() -> Unit
    ) {
        clear()
        scope.lazyListScope = lazyListScope
        scope.block()
    }
}

fun LazyListScope.startRecord(
    recorder: ItemRecorder,
    block: LazyListRecordScope.() -> Unit
) {
    recorder.startRecord(
        lazyListScope = this,
        block = block
    )
}