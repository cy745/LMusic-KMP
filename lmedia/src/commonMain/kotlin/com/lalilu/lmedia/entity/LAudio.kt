package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class LAudio(
    override val id: String = "",
    override val title: String = "",
    override val subtitle: String = "",
    override val extra: Map<String, String> = emptyMap(),

    @Transient
    var sourceItem: SourceItem = Empty
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

/**
 * 远程访问使用的标记SourceItem，
 * Server端通过此Item找到本机上的对应元素并返回对应数据
 */
data class Remote(
    val id: String,
    val type: String
) : SourceItem {
    override val key: String = "${this::class.qualifiedName}_$id"
}