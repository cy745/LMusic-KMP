package com.lalilu.lmedia.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass

@Serializable
data class LAudio(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("subtitle") val subtitle: String = "",
    @SerialName("extra") val extra: Map<String, String> = EMPTY_EXTRA,

    @Transient
    var sourceItem: SourceItem = SourceItemDefaults.Empty,
    var metadata: Metadata = Metadata.EMPTY,
    var mediaSourceName: String,
) : LItem {
    companion object {
        val EMPTY_EXTRA = emptyMap<String, String>()
    }

    // Identifiable implementation
    override fun id(): String = id

    // Describable implementation
    override fun title(): String = title
    override fun subtitle(): String = subtitle

    // Extensible implementation
    override fun extra(): Map<String, String> = extra

    // Linkable implementation
    @Transient
    override val refs = mutableMapOf<KClass<*>, MutableSet<Linkable>>()
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
