/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.lhistory.entity

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
    val id: Long = 0L,

    val contentId: String,
    val contentTitle: String,
    val parentId: String = "",
    val parentTitle: String = "",

    // 数据库层面0为正常值，而-1代表预保存记录，即会被清除的记录
    val duration: Long = -1L,
    val repeatCount: Int = 0,
    val startTime: Long = 0L,
)