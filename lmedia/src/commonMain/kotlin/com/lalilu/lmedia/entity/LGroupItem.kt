package com.lalilu.lmedia.entity

interface LGroupItem : LItem {
    val items: List<LItem>
    val itemsCount: Int
        get() = items.size
}