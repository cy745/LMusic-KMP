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

package com.lalilu.lmedia.data.database.relation

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "cross_ref_audio_x_artist",
    primaryKeys = ["artist_id", "song_id"]
)
data class CrossRefLAudioXLArtist(
    @ColumnInfo("artist_id")
    val artistId: String,
    @ColumnInfo("song_id")
    val songId: String
)