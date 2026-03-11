package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query


@Entity(tableName = "song_table")
data class Song(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "data") val text: String
)

@Dao
interface SongDao {
    @Insert
    suspend fun insert(entity: Song)

    @Query("SELECT * FROM song_table")
    suspend fun getAll(): List<Song>
}