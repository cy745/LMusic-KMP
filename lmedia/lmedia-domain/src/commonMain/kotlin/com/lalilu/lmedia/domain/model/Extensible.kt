package com.lalilu.lmedia.domain.model

interface Extensible {
    fun extraValue(): Map<String, String>
}

fun extensibleImpl(extra: () -> Map<String, String>?) = object : Extensible {
    override fun extraValue(): Map<String, String> = extra() ?: emptyMap()
}
