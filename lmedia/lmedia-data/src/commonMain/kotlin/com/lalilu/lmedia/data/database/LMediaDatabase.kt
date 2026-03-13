package com.lalilu.lmedia.data.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist


@Database(
    version = 1,
    entities = [LAudio::class, LArtist::class, CrossRefLAudioXLArtist::class],
    exportSchema = true,
)
@ConstructedBy(LMediaDatabaseConstructor::class)
abstract class LMediaDatabase : RoomDatabase() {
    abstract fun audioDao(): LAudioDao
    abstract fun artistDao(): LArtistDao
}

expect object LMediaDatabaseConstructor : RoomDatabaseConstructor<LMediaDatabase> {
    override fun initialize(): LMediaDatabase
}