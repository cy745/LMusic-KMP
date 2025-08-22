package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class LAudio(
    override val id: String = "",
    override val title: String = "",
    override val subtitle: String = "",
    override val extra: Map<String, String> = emptyMap(),

    @Transient
    var sourceItem: SourceItem = Empty,
    var mediaSourceName: String,
) : LItem

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect sealed interface SourceItem {
    val key: String
}

/**
 * 标记无数据的对象
 */
object Empty : SourceItem {
    override val key: String = "${this::class::qualifiedName}"
}