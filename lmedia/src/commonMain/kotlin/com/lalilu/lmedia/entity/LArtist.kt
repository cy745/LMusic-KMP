package com.lalilu.lmedia.entity

class LArtist(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val extra: Map<String, String>
) : LItem {
}