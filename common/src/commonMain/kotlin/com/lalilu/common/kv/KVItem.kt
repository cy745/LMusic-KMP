package com.lalilu.common.kv

//import androidx.annotation.CallSuper
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class KVItem<T> : MutableState<T>, ReadWriteProperty<KVItem<T>, T>, UpdatableKV<T> {
    var autoSave = true
        private set

    private val state: MutableState<T> by lazy { mutableStateOf(getData()) }
    private val flowInternal: MutableStateFlow<T> by lazy { MutableStateFlow(state.value) }
    override var value: T
        get() = state.value
        set(value) {
            val oldValue = state.value
            state.value = value
            if (oldValue != value && autoSave) {
                setData(value)
            }
        }

    override fun getValue(thisRef: KVItem<T>, property: KProperty<*>): T = thisRef.value
    override fun setValue(thisRef: KVItem<T>, property: KProperty<*>, value: T) =
        run { thisRef.value = value }

    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }

    override fun save() = setData(value)
    override fun update() = run { value = getData() }
    override fun flow(): Flow<T> = flowInternal
    override fun enableAutoSave() = run { autoSave = true }
    override fun disableAutoSave() = run { autoSave = false }

//    @CallSuper
    override fun setData(value: T) {
        flowInternal.tryEmit(value)
    }
}