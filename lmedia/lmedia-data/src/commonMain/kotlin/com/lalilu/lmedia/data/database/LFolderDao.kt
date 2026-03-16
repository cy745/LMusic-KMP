package com.lalilu.lmedia.data.database

import androidx.room3.*
import com.lalilu.lmedia.entity.LFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface LFolderDao {
    @Insert
    suspend fun insert(folder: LFolder)

    @Update
    suspend fun update(folder: LFolder)

    @Delete
    suspend fun delete(folder: LFolder)

    @Transaction
    @Query("SELECT * FROM l_folder")
    fun getAllFolder(): Flow<List<LFolder>>

    @Transaction
    @Query("SELECT * FROM l_folder WHERE folder_id = :id")
    fun getFolder(id: String): Flow<LFolder?>
}
