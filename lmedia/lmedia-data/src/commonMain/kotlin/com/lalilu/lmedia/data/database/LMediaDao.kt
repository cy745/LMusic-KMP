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

package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Transaction
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.ref
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist

@Dao
interface LMediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudio(list: List<LAudio>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(list: List<LArtist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(list: List<CrossRefLAudioXLArtist>)

    @Transaction
    suspend fun insert(snapshot: Snapshot) {
        insertAudio(list = snapshot.audios)
        insertArtist(list = snapshot.artists)

        val relations = snapshot.audios.flatMap { song ->
            val artists = song.ref<LArtist>()
            artists.map { artist ->
                CrossRefLAudioXLArtist(
                    songId = song.idValue(),
                    artistId = artist.idValue()
                )
            }
        }

        insertRelation(list = relations)
    }
}