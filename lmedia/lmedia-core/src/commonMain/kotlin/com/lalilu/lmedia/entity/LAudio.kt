package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass

@Entity(tableName = "l_audio")
@Serializable
data class LAudio(
    @PrimaryKey
    @ColumnInfo("song_id")
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",

    @Ignore
    @Transient
    val extra: Map<String, String> = EMPTY_EXTRA,

    @Ignore
    @Transient
    var sourceItem: SourceItem = SourceItemDefaults.Empty,

    @Ignore
    var metadata: Metadata = Metadata.EMPTY,
    var mediaSourceName: String = "",
) : LItem {
    companion object {
        val EMPTY_EXTRA = emptyMap<String, String>()
    }

    // Identifiable implementation
    override fun idValue(): String = id

    // Describable implementation
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle

    // Extensible implementation
    override fun extraValue(): Map<String, String> = extra

    // Linkable implementation
    @Ignore
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
