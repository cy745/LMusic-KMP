package com.lalilu.lmedia.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "l_audio")
data class LAudioEntity(
    @PrimaryKey
    @ColumnInfo("song_id")
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    @ColumnInfo("media_source_name")
    val mediaSourceName: String = "",
    /**
     * 仅用于读取旧版本数据库中的 metadata JSON。
     *
     * 保留相同的列名和非空 TEXT 类型可以避免数据库迁移；领域模型不再暴露 Metadata，旧数据会在
     * AudioMapper 中合并到 extra，新写入的数据固定保存为空对象。
     */
    @ColumnInfo("metadata")
    val legacyMetadataJson: String = "{}",
    val extra: Map<String, String>? = null,
    val available: Boolean = true
)
