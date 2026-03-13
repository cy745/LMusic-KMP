package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.lalilu.lmedia.entity.LAudio

@Dao
interface LAudioDao {
    @Insert
    suspend fun insert(audio: LAudio)

    @Update
    suspend fun update(audio: LAudio)

    @Delete
    suspend fun delete(audio: LAudio)

    @Query("SELECT * FROM l_audio")
    suspend fun getAll(): List<LAudio>
}