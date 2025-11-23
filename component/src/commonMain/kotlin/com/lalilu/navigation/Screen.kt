package com.lalilu.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


interface Screen : NavKey {
    val key: String
        get() = name()

    @Composable
    fun Content()
}

private fun Screen.name(): String {
    return this::class.qualifiedName
        ?: error("Attempted to get the name of a local or anonymous screen")
}

@OptIn(ExperimentalUuidApi::class)
fun uniqueScreenKey(): String {
    return "Screen#${Uuid.random().toHexString()}"
}

fun Screen.toNavEntry(): NavEntry<Screen> {
    val metadata = mutableMapOf<String, Any>()

    if (this is ScreenTransitionFactory) {
        metadata += provideTransitionMetadata()
    }

    return NavEntry(
        key = this,
        contentKey = this.key,
        metadata = metadata,
        content = { it.Content() }
    )
}