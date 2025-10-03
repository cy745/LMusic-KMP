package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class LAudio(
    override val id: String = "",
    override val title: String = "",
    override val subtitle: String = "",
    override val extra: Map<String, String> = EMPTY_EXTRA,

    @Transient
    var sourceItem: SourceItem = SourceItemDefaults.Empty,
    var metadata: Metadata = Metadata.EMPTY,
    var mediaSourceName: String,
) : LItem {
    companion object {
        val EMPTY_EXTRA = emptyMap<String, String>()
    }
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect sealed interface SourceItem {
    val key: String
}

object SourceItemDefaults {
    /**
     * 标记无数据的对象
     */
    object Empty : SourceItem {
        override val key: String = "Empty"
    }

    /**
     * 向后端请求url
     */
    object RequestUrl : SourceItem {
        override val key: String = "RequestUrl"
    }
}