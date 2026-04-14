package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import com.lalilu.lmedia.sortable.Sortable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(tableName = "l_audio", ignoredColumns = ["refs"])
@Serializable
data class LAudio(
    @PrimaryKey
    @ColumnInfo("song_id")
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",
    @ColumnInfo("media_source_name")
    var mediaSourceName: String = "",
    var metadata: Metadata = Metadata.EMPTY,
    var extra: Map<String, String>? = null,
    override var available: Boolean = true,

    @Ignore
    @Transient
    var sourceItem: SourceItem = SourceItemDefaults.Empty,
) : LItem, Sourceable, Available, Playable, TextMatchable, Sortable,
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

    override fun getMatchText(): String = "${title}_${subtitle}"

    @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
    override fun <T : Any> getValueBy(key: String): T? {
        return when (key) {
            Sortable.COMPARE_KEY_ID -> id
            Sortable.COMPARE_KEY_TITLE -> extra?.get("title") ?: metadata.title ?: title
            Sortable.COMPARE_KEY_SUB_TITLE -> extra?.get("subtitle") ?: metadata.artist ?: subtitle
            Sortable.COMPARE_KEY_CREATE_TIME -> extra?.get("date_added")?.toLongOrNull() ?: metadata.dateAdded
            Sortable.COMPARE_KEY_MODIFY_TIME -> extra?.get("date_modified")?.toLongOrNull() ?: metadata.dateModified
            Sortable.COMPARE_KEY_CONTENT_TYPE -> extra?.get("content_type")
            Sortable.COMPARE_KEY_FILE_SIZE -> extra?.get("file_size")
            Sortable.COMPARE_KEY_DISK_NUMBER -> extra?.get("disc") ?: metadata.disc
            Sortable.COMPARE_KEY_TRACK_NUMBER -> extra?.get("track") ?: metadata.track
            Sortable.COMPARE_KEY_DURATION -> extra?.get("duration")?.toLongOrNull() ?: metadata.duration
            else -> super.getValueBy<T>(key)
        } as? T?
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
