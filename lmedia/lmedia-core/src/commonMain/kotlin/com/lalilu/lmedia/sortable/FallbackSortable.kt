package com.lalilu.lmedia.sortable

import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.model.dateAdded
import com.lalilu.lmedia.domain.model.dateModified
import com.lalilu.lmedia.domain.model.disc
import com.lalilu.lmedia.domain.model.duration
import com.lalilu.lmedia.domain.model.track

/**
 * Creates a [Sortable] proxy for domain model types that don't implement
 * [Sortable] themselves. Used internally by [SortRuleNormal] when sorting
 * lists of domain models.
 */
fun Any.toFallbackSortable(): Sortable = object : Sortable {
    override fun <T : Any> getValueBy(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return when (val self = this@toFallbackSortable) {
            is LAudio -> when (key) {
                Sortable.COMPARE_KEY_ID -> self.id
                Sortable.COMPARE_KEY_TITLE -> self.title
                Sortable.COMPARE_KEY_SUB_TITLE -> self.artistName
                Sortable.COMPARE_KEY_DURATION -> self.duration
                Sortable.COMPARE_KEY_CREATE_TIME -> self.dateAdded
                Sortable.COMPARE_KEY_MODIFY_TIME -> self.dateModified
                Sortable.COMPARE_KEY_TRACK_NUMBER -> self.track
                Sortable.COMPARE_KEY_DISK_NUMBER -> self.disc
                Sortable.COMPARE_KEY_FILE_SIZE -> self.extra?.get("file_size")
                Sortable.COMPARE_KEY_CONTENT_TYPE -> self.extra?.get("content_type")
                else -> null
            } as? T?

            is LAlbum -> when (key) {
                Sortable.COMPARE_KEY_ID -> self.id
                Sortable.COMPARE_KEY_TITLE -> self.title
                Sortable.COMPARE_KEY_SUB_TITLE -> self.subtitle
                else -> null
            } as? T?

            is LArtist -> when (key) {
                Sortable.COMPARE_KEY_ID -> self.id
                Sortable.COMPARE_KEY_TITLE -> self.title
                Sortable.COMPARE_KEY_SUB_TITLE -> self.subtitle
                else -> null
            } as? T?

            else -> null
        }
    }
}
