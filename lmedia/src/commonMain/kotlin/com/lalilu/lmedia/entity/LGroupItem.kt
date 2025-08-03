package com.lalilu.lmedia.entity

interface LGroupItem : LItem {
    val items: List<LAudio>
    val itemsCount: Int
        get() = items.size
}