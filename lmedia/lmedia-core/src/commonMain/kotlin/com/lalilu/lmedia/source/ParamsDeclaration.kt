package com.lalilu.lmedia.source

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data class ParamsDeclaration(
    val key: String,
    val name: String,
    val description: String,
    val mutable: Boolean = true,
    val required: Boolean = false,
    val type: KClass<out MediaSourceParam>,
)
