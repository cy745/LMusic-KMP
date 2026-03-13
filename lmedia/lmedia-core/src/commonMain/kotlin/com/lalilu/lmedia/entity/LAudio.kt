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

    /**
     * 将指定的分组项链接到当前音频对象
     *
     * @param groupItem 要链接的分组项，必须是 LGroupItem 的子类实例
     * @param T 分组项的具体类型，必须继承自 LGroupItem
     */
    inline fun <reified T : LGroupItem> link(groupItem: T) {
        refs.getOrPut(T::class) { mutableSetOf() }
            .add(groupItem)
    }

    /**
     * 获取当前音频对象关联的指定类型的分组项列表
     *
     * @param T 要获取的分组项类型，必须继承自 LGroupItem
     * @return 包含所有关联的指定类型分组项的列表，如果没有则返回空列表
     */
    inline fun <reified T : LGroupItem> ref(): List<T> {
        return refs[T::class]?.map { it as T } ?: emptyList()
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