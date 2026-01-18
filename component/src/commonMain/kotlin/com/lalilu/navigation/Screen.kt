package com.lalilu.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


interface Screen : NavKey {
    val key: String
        get() = name()

    @Composable
    fun Content()
}

data class ScreenWrapper(
    val screen: Screen,
    val metadata: MutableMap<String, Any?> = mutableMapOf()
) : Screen by screen

fun Screen.wrapWith(metadata: Map<String, Any?>): Screen {
    if (this is ScreenWrapper) {
        this.metadata.putAll(metadata)
        return this
    }

    return ScreenWrapper(
        screen = this,
        metadata = metadata.toMutableMap()
    )
}

fun Screen.isType(type: KClass<*>): Boolean {
    if (this is ScreenWrapper) {
        return type.isInstance(screen)
    }
    return type.isInstance(this)
}

fun Screen.toNavEntry(): NavEntry<Screen> {
    var screen = this
    val metadata: MutableMap<String, Any>

    if (screen is ScreenWrapper) {
        metadata = screen.metadata.toNonNullMap().toMutableMap()
        screen = screen.screen
    } else {
        metadata = mutableMapOf()
    }

    if (screen is ScreenMetadataFactory) {
        metadata += screen.provideMetadata()
    }

    return NavEntry(
        key = this,
        contentKey = this.key,
        metadata = metadata,
        content = { it.Content() }
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toNonNullMap(): Map<String, Any> {
    return this.filterValues { it != null } as Map<String, Any>
}

private fun Screen.name(): String {
    return this::class.qualifiedName
        ?: error("Attempted to get the name of a local or anonymous screen")
}

@OptIn(ExperimentalUuidApi::class)
fun uniqueScreenKey(): String {
    return "Screen#${Uuid.random().toHexString()}"
}