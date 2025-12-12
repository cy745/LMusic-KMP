package com.lalilu.lmedia.entity

interface LItem {
    val id: String
    val title: String
    val subtitle: String
    val extra: Map<String, String>
}