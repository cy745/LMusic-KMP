package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable

@Serializable
class LAlbum(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val extra: Map<String, String>,
    override val items: List<LItem> = emptyList()
) : LGroupItem {
}