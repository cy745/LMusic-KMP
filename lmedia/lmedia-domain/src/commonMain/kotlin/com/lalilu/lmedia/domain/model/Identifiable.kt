package com.lalilu.lmedia.domain.model

interface Identifiable {
    fun idValue(): String
    fun idPrefix(): String = ""
}
