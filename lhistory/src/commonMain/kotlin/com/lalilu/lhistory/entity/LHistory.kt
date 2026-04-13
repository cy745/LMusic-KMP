/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lalilu.lhistory.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * 播放历史记录实体
 *
 * @param id 主键，自增
 * @param contentId 内容的唯一标识符（对应音频ID）
 * @param contentTitle 内容标题
 * @param parentId 父级ID（如所属专辑/文件夹ID）
 * @param parentTitle 父级标题
 * @param duration 播放时长，-1L 表示预保存记录（会被清理），0 为正常值
 * @param repeatCount 重复播放次数
 * @param startTime 开始播放的时间戳
 */
@Entity(tableName = "m_history")
data class LHistory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    val id: Long = 0L,

    @ColumnInfo("content_id")
    val contentId: String,

    @ColumnInfo("content_title")
    val contentTitle: String,

    @ColumnInfo("parent_id")
    val parentId: String = "",

    @ColumnInfo("parent_title")
    val parentTitle: String = "",

    // 数据库层面0为正常值，而-1代表预保存记录，即会被清除的记录
    @ColumnInfo("duration")
    val duration: Long = -1L,

    @ColumnInfo("repeat_count")
    val repeatCount: Int = 0,

    @ColumnInfo("start_time")
    val startTime: Long = 0L,
)