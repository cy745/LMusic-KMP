package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(tableName = "l_audio")
@Serializable
data class LAudio(
    @PrimaryKey
    @ColumnInfo("song_id")
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",
    var mediaSourceName: String = "",
    var metadata: Metadata = Metadata.EMPTY,
    var extra: Map<String, String>? = null,
    override var available: Boolean = false,

    @Ignore
    @Transient
    var sourceItem: SourceItem = SourceItemDefaults.Empty,
) : LItem, Sourceable, Available, Playable,
    Linkable by linkableImpl(),
    Extensible by extensibleImpl({ extra }) {

    // Identifiable implementation
    override fun idValue(): String = id

    // Describable implementation
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle

    // Sourceable implementation
    override fun source(): String = mediaSourceName

    // Playable implementation
    override fun sourceItem(): SourceItem = sourceItem
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
