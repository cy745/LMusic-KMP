package com.lalilu.lmedia.source

import kotlin.reflect.KClass

data class ParamsDeclaration(
    val key: String,
    val name: String,
    val description: String,
    val mutable: Boolean = true,
    val required: Boolean = false,
    val type: KClass<*>,
)
