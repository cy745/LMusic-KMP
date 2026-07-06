package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.lalilu.lmedia.data.entity.LFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LFolderDao {
    @Insert
    suspend fun insert(folder: LFolderEntity)

    @Update
    suspend fun update(folder: LFolderEntity)

    @Delete
    suspend fun delete(folder: LFolderEntity)

    @Transaction
    @Query("SELECT * FROM l_folder")
    fun getAllFolder(): Flow<List<LFolderEntity>>

    @Transaction
    @Query("SELECT * FROM l_folder WHERE folder_id = :id")
    fun getFolder(id: String): Flow<LFolderEntity?>
}
