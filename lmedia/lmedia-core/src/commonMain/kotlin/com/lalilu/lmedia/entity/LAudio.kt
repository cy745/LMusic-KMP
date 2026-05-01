package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import com.lalilu.lmedia.sortable.Sortable
import com.lalilu.lmedia.source.MediaSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass

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
) : LItem, Sourceable, Available, Playable, TextMatchable, Sortable, Linkable,
    Extensible by extensibleImpl({ extra }) {
    override val refs: MutableMap<KClass<*>, MutableSet<Linkable>> = mutableMapOf()

    // Identifiable implementation
    override fun idValue(): String = id
    override fun idPrefix(): String = ID_PREFIX

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

    companion object {
        const val ID_PREFIX = "audio_"
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

interface BuildAudioScope {
    fun title(title: String?)
    fun subtitle(subtitle: String?)
    fun metadata(metadata: Metadata)
    fun extra(extra: Map<String, String>)
    fun source(source: SourceItem)
    fun direct(block: LAudio.() -> Unit)
}

context(source: MediaSource)
fun buildAudio(id: String, block: BuildAudioScope.() -> Unit = {}): LAudio {
    return LAudio(id = "${LAudio.ID_PREFIX}$id", mediaSourceName = source.name).apply {
        object : BuildAudioScope {
            override fun title(title: String?) {
                this@apply.title = title?.takeIf { it.isNotBlank() } ?: "Unknown"
            }

            override fun subtitle(subtitle: String?) {
                this@apply.subtitle = subtitle?.takeIf { it.isNotBlank() } ?: "Unknown Subs"
            }

            override fun metadata(metadata: Metadata) {
                this@apply.metadata = metadata
            }

            override fun extra(extra: Map<String, String>) {
                this@apply.extra = extra
            }

            override fun source(source: SourceItem) {
                this@apply.sourceItem = source
            }

            override fun direct(block: LAudio.() -> Unit) {
                this@apply.block()
            }
        }.apply(block)
    }
}